package sttp.tapir.server.netty.zio

import cats.effect.{IO, Resource}
import io.netty.channel.nio.NioEventLoopGroup
import org.scalatest.EitherValues
import org.scalatest.Exceptional
import org.scalatest.FutureOutcome
import org.scalatest.concurrent.TimeLimits
import scala.concurrent.Future
import scala.concurrent.duration._
import sttp.capabilities.zio.ZioStreams
import sttp.monad.MonadError
import sttp.tapir.server.netty.internal.FutureUtil
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}
import sttp.tapir.ztapir.RIOMonadError
import zio.interop.catz._
import zio.{Task, ZIO}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import zio.stream.ZSink

class NettyZioServerTest extends TestSuite with EitherValues with TimeLimits {

  // ZIO tests sometimes hang, esp. on CI (see #3827); until this is fixed, adding retries to avoid flaky tests & CI timeouts
  val retries = 5

  override def withFixture(test: NoArgAsyncTest): FutureOutcome = withFixture(test, retries)

  def withFixture(test: NoArgAsyncTest, count: Int): FutureOutcome = {
    val outcome = failAfter(5.seconds)(super.withFixture(test))
    new FutureOutcome(outcome.toFuture.flatMap {
      case Exceptional(e) =>
        println(s"Test ${test.name} failed, retrying.")
        e.printStackTrace()
        (if (count == 1) super.withFixture(test) else withFixture(test, count - 1)).toFuture
      case other => Future.successful(other)
    })
  }
  //

  def drainZStream(zStream: ZioStreams.BinaryStream): Task[Unit] =
    zStream.run(ZSink.drain)

  override def tests: Resource[IO, List[Test]] =
    backendResource.flatMap { backend =>
      Resource
        .make {
          implicit val monadError: MonadError[Task] = new RIOMonadError[Any]
          val eventLoopGroup = new NioEventLoopGroup()

          val interpreter = new NettyZioTestServerInterpreter(eventLoopGroup)
          val createServerTest = new DefaultCreateServerTest(backend, interpreter)
          val zioSleeper: Sleeper[Task] = new Sleeper[Task] {
            override def sleep(duration: FiniteDuration): Task[Unit] = ZIO.sleep(zio.Duration.fromScala(duration))
          }

          val tests =
            new AllServerTests(
              createServerTest,
              interpreter,
              backend,
              staticContent = false,
              multipart = false
            ).tests() ++
              new ServerStreamingTests(createServerTest).tests(ZioStreams)(drainZStream) ++
              new ServerCancellationTests(createServerTest)(monadError, asyncInstance).tests() ++
              new ServerGracefulShutdownTests(createServerTest, zioSleeper).tests()

          IO.pure((tests, eventLoopGroup))
        } { case (_, eventLoopGroup) =>
          IO.fromFuture(IO.delay(FutureUtil.nettyFutureToScala(eventLoopGroup.shutdownGracefully()): Future[_])).void
        }
        .map { case (tests, _) => tests }
    }
}
