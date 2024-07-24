package sttp.tapir.server.netty.zio

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
import zio.{Task, ZIO}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
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
