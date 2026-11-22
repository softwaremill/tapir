package sttp.tapir.server.netty.zio

import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Resource}
import io.netty.channel.nio.NioEventLoopGroup
import org.scalatest.EitherValues
import sttp.capabilities.zio.ZioStreams
import sttp.monad.MonadError
import sttp.tapir.server.netty.internal.FutureUtil
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}
import sttp.tapir.ztapir.RIOMonadError
import zio.interop.catz._
import zio.{Runtime, Task, Unsafe, ZIO}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import zio.stream.ZSink

class NettyZioServerTest extends TestSuite with EitherValues {
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
              partOtherHeaderSupport = false
            ).tests() ++
              new ServerStreamingTests(createServerTest).tests(ZioStreams)(drainZStream) ++
              new ServerCancellationTests(createServerTest)(using monadError, asyncInstance).tests() ++
              new ServerGracefulShutdownTests(createServerTest, zioSleeper).tests() ++
              List(stopTwiceTest)

          IO.pure((tests, eventLoopGroup))
        } { case (_, eventLoopGroup) =>
          IO.fromFuture(IO.delay(FutureUtil.nettyFutureToScala(eventLoopGroup.shutdownGracefully()): Future[?])).void
        }
        .map { case (tests, _) => tests }
    }

  private def stopTwiceTest: Test = Test("stopping a server more than once") {
    val stopTwice = NettyZioServer[Any]().port(0).start().flatMap(server => server.stop() *> server.stop())
    // not IO.fromFuture, which can't be timed out
    IO.blocking(Await.result(Unsafe.unsafe(implicit u => Runtime.default.unsafe.runToFuture(stopTwice)), 10.seconds))
      .map(_ => succeed)
      .unsafeToFuture()
  }
}
