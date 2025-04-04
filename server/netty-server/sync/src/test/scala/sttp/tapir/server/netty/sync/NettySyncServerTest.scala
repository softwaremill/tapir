package sttp.tapir.server.netty.sync

import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.netty.channel.nio.NioEventLoopGroup
import org.scalactic.source.Position
import org.scalatest.BeforeAndAfterAll
import org.scalatest.compatible.Assertion
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers.*
import org.slf4j.LoggerFactory
import ox.*
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4.*
import sttp.model.*
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.tests.*
import sttp.tapir.tests.*

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import ox.flow.Flow
import scala.annotation.nowarn
import sttp.tapir.server.netty.NettySyncRequestTimeoutTests

class NettySyncServerTest extends AsyncFunSuite with BeforeAndAfterAll {

  val (backend, stopBackend) = backendResource.allocated.unsafeRunSync()
  def testNameFilter: Option[String] = None // define to run a single test (temporarily for debugging)
  {
    val eventLoopGroup = new NioEventLoopGroup()

    val interpreter = new NettySyncTestServerInterpreter(eventLoopGroup)
    val createServerTest = new NettySyncCreateServerTest(backend, interpreter)
    val sleeper: Sleeper[Identity] = (duration: FiniteDuration) => Thread.sleep(duration.toMillis)

    val tests =
      new AllServerTests(createServerTest, interpreter, backend, staticContent = false, multipart = false)
        .tests() ++
        new ServerGracefulShutdownTests(createServerTest, sleeper).tests() ++
        new ServerWebSocketTests(createServerTest, OxStreams, autoPing = true, handlePong = true) {
          override def functionToPipe[A, B](f: A => B): OxStreams.Pipe[A, B] = _.map(f)
          override def emptyPipe[A, B]: OxStreams.Pipe[A, B] = _ => Flow.empty
        }.tests() ++
        NettySyncRequestTimeoutTests(eventLoopGroup, backend).tests()

    tests.foreach { t =>
      if (testNameFilter.forall(filter => t.name.contains(filter))) {
        @nowarn implicit val pos: Position = t.pos // used by test macro

        this.test(t.name)(t.f())
      }
    }
  }
  override protected def afterAll(): Unit = {
    stopBackend.unsafeRunSync()
    super.afterAll()
  }
}

class NettySyncCreateServerTest(
    backend: WebSocketStreamBackend[IO, Fs2Streams[IO]],
    interpreter: NettySyncTestServerInterpreter
) extends CreateServerTest[Identity, OxStreams & WebSockets, NettySyncServerOptions, IdRoute] {

  private val logger = LoggerFactory.getLogger(getClass.getName)

  override def testServer[I, E, O](
      e: PublicEndpoint[I, E, O, OxStreams & WebSockets],
      testNameSuffix: String = "",
      interceptors: Interceptors = identity
  )(
      fn: I => Identity[Either[E, O]]
  )(runTest: (WebSocketStreamBackend[IO, Fs2Streams[IO]], Uri) => IO[Assertion]): Test = {
    testServerLogic(e.serverLogic(fn), testNameSuffix, interceptors)(runTest)
  }

  override def testServerLogic(
      e: ServerEndpoint[OxStreams & WebSockets, Identity],
      testNameSuffix: String = "",
      interceptors: Interceptors = identity
  )(
      runTest: (WebSocketStreamBackend[IO, Fs2Streams[IO]], Uri) => IO[Assertion]
  ): Test = {
    testServerLogicWithStop(e, testNameSuffix, interceptors)((_: IO[Unit]) => runTest)
  }

  override def testServerLogicWithStop(
      e: ServerEndpoint[OxStreams & WebSockets, Identity],
      testNameSuffix: String = "",
      interceptors: Interceptors = identity,
      gracefulShutdownTimeout: Option[FiniteDuration] = None
  )(
      runTest: IO[Unit] => (WebSocketStreamBackend[IO, Fs2Streams[IO]], Uri) => IO[Assertion]
  ): Test = {
    Test(
      e.showDetail + (if (testNameSuffix == "") "" else " " + testNameSuffix)
    ) {
      supervised {
        val binding = interpreter.scopedServerWithInterceptorsStop(e, interceptors, gracefulShutdownTimeout)
        val assertion: Assertion =
          runTest(IO.blocking(binding.stop()))(backend, uri"http://localhost:${binding.port}")
            .guarantee(IO(logger.info(s"Test completed on port ${binding.port}")))
            .unsafeRunSync()
        Future.successful(assertion)
      }
    }
  }

  override def testServerWithStop(name: String, rs: => NonEmptyList[IdRoute], gracefulShutdownTimeout: Option[FiniteDuration])(
      runTest: IO[Unit] => (WebSocketStreamBackend[IO, Fs2Streams[IO]], Uri) => IO[Assertion]
  ): Test = throw new UnsupportedOperationException

  override def testServer(name: String, rs: => NonEmptyList[IdRoute])(
      runTest: (WebSocketStreamBackend[IO, Fs2Streams[IO]], Uri) => IO[Assertion]
  ): Test =
    Test(name) {
      supervised {
        val binding = interpreter.scopedServerWithRoutesStop(rs)
        val assertion: Assertion =
          runTest(backend, uri"http://localhost:${binding.port}")
            .guarantee(IO(logger.info(s"Test completed on port ${binding.port}")))
            .unsafeRunSync()
        Future.successful(assertion)
      }
    }

  def testServer(name: String, es: NonEmptyList[ServerEndpoint[OxStreams & WebSockets, Identity]])(
      runTest: (WebSocketStreamBackend[IO, Fs2Streams[IO]], Uri) => IO[Assertion]
  ): Test = {
    Test(name) {
      supervised {
        val binding = interpreter.scopedServerWithStop(es)
        val assertion: Assertion =
          runTest(backend, uri"http://localhost:${binding.port}")
            .guarantee(IO(logger.info(s"Test completed on port ${binding.port}")))
            .unsafeRunSync()
        Future.successful(assertion)
      }
    }
  }
}
