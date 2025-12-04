package sttp.tapir.server.tests

import cats.data.NonEmptyList
import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Resource}
import cats.implicits._
import org.scalatest.Assertion
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4._
import sttp.model._
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.CustomiseInterceptors
import sttp.tapir.tests._
import org.slf4j.LoggerFactory

import scala.concurrent.duration.FiniteDuration

trait CreateServerTest[F[_], +R, OPTIONS, ROUTE] {
  protected type Interceptors = CustomiseInterceptors[F, OPTIONS] => CustomiseInterceptors[F, OPTIONS]

  def testServer[I, E, O](
      e: PublicEndpoint[I, E, O, R],
      testNameSuffix: String = "",
      interceptors: Interceptors = identity
  )(fn: I => F[Either[E, O]])(runTest: (WebSocketStreamBackend[IO, Fs2Streams[IO]], Uri) => IO[Assertion]): Test

  def testServerLogic(
      e: ServerEndpoint[R, F],
      testNameSuffix: String = "",
      interceptors: Interceptors = identity
  )(
      runTest: (WebSocketStreamBackend[IO, Fs2Streams[IO]], Uri) => IO[Assertion]
  ): Test

  def testServerLogicWithStop(
      e: ServerEndpoint[R, F],
      testNameSuffix: String = "",
      interceptors: Interceptors = identity,
      gracefulShutdownTimeout: Option[FiniteDuration] = None
  )(
      runTest: KillSwitch => (WebSocketStreamBackend[IO, Fs2Streams[IO]], Uri) => IO[Assertion]
  ): Test

  def testServer(name: String, r: => ROUTE)(
      runTest: (WebSocketStreamBackend[IO, Fs2Streams[IO]], Uri) => IO[Assertion]
  ): Test

  /** Override for a server to allow running tests which have access to a stop() effect, allowing shutting down the server within the test.
    * By default, this method just uses a no-op IO.unit.
    */
  def testServerWithStop(name: String, r: => ROUTE, gracefulShutdownTimeout: Option[FiniteDuration])(
      runTest: KillSwitch => (WebSocketStreamBackend[IO, Fs2Streams[IO]], Uri) => IO[Assertion]
  ): Test
}

class DefaultCreateServerTest[F[_], +R, OPTIONS, ROUTE](
    backend: WebSocketStreamBackend[IO, Fs2Streams[IO]],
    interpreter: TestServerInterpreter[F, R, OPTIONS, ROUTE]
) extends CreateServerTest[F, R, OPTIONS, ROUTE] {

  private val logger = LoggerFactory.getLogger(getClass.getName)

  override def testServer[I, E, O](
      e: PublicEndpoint[I, E, O, R],
      testNameSuffix: String = "",
      interceptors: Interceptors = identity
  )(
      fn: I => F[Either[E, O]]
  )(runTest: (WebSocketStreamBackend[IO, Fs2Streams[IO]], Uri) => IO[Assertion]): Test = {
    testServer(
      e.showDetail + (if (testNameSuffix == "") "" else " " + testNameSuffix),
      interpreter.route(e.serverLogic(fn), interceptors)
    )(runTest)
  }

  override def testServerLogicWithStop(
      e: ServerEndpoint[R, F],
      testNameSuffix: String = "",
      interceptors: Interceptors = identity,
      gracefulShutdownTimeout: Option[FiniteDuration] = None
  )(
      runTest: IO[Unit] => (WebSocketStreamBackend[IO, Fs2Streams[IO]], Uri) => IO[Assertion]
  ): Test = {
    testServerWithStop(
      e.showDetail + (if (testNameSuffix == "") "" else " " + testNameSuffix),
      interpreter.route(e, interceptors),
      gracefulShutdownTimeout
    )(runTest)
  }
  override def testServerLogic(
      e: ServerEndpoint[R, F],
      testNameSuffix: String = "",
      interceptors: Interceptors = identity
  )(
      runTest: (WebSocketStreamBackend[IO, Fs2Streams[IO]], Uri) => IO[Assertion]
  ): Test = {
    testServer(
      e.showDetail + (if (testNameSuffix == "") "" else " " + testNameSuffix),
      interpreter.route(e, interceptors)
    )(runTest)
  }

  override def testServerWithStop(name: String, r: => ROUTE, gracefulShutdownTimeout: Option[FiniteDuration])(
      runTest: IO[Unit] => (WebSocketStreamBackend[IO, Fs2Streams[IO]], Uri) => IO[Assertion]
  ): Test = {
    val resources = for {
      portAndStop <- interpreter.serverWithStop(NonEmptyList.of(r), gracefulShutdownTimeout).onError { case e: Exception =>
        Resource.eval(IO(logger.error(s"Starting server failed because of ${e.getMessage}")))
      }
      _ <- Resource.eval(IO(logger.info(s"Bound server on port: ${portAndStop._1}")))
    } yield portAndStop

    Test(name)(
      resources
        .use { case (port, stopServer) =>
          runTest(stopServer)(backend, uri"http://localhost:$port").guarantee(IO(logger.info(s"Tests completed on port $port")))
        }
        .unsafeToFuture()
    )
  }
  override def testServer(name: String, r: => ROUTE)(
      runTest: (WebSocketStreamBackend[IO, Fs2Streams[IO]], Uri) => IO[Assertion]
  ): Test = {
    val resources = for {
      port <- interpreter.server(NonEmptyList.of(r)).onError { case e: Exception =>
        Resource.eval(IO(logger.error(s"Starting server failed because of ${e.getMessage}")))
      }
      _ <- Resource.eval(IO(logger.info(s"Bound server on port: $port")))
    } yield port

    Test(name)(
      resources
        .use { port =>
          runTest(backend, uri"http://localhost:$port")
            .guaranteeCase(exitCase => IO(logger.info(s"Test on port $port: ${exitCase.getClass.getSimpleName}")))
        }
        .unsafeToFuture()
    )
  }
}
