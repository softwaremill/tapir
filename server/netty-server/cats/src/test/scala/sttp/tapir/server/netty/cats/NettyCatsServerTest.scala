package sttp.tapir.server.netty.cats

import cats.effect.{IO, Resource}
import io.netty.channel.nio.NioEventLoopGroup
import org.scalatest.EitherValues
import sttp.capabilities.fs2.Fs2Streams
import sttp.monad.MonadError
import sttp.tapir.integ.cats.effect.CatsMonadError
import sttp.tapir.server.netty.internal.FutureUtil
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class NettyCatsServerTest extends TestSuite with EitherValues {

  override def tests: Resource[IO, List[Test]] =
    backendResource.flatMap { backend =>
      Resource
        .make {
          implicit val m: MonadError[IO] = new CatsMonadError[IO]()
          val eventLoopGroup = new NioEventLoopGroup()

          val interpreter = new NettyCatsTestServerInterpreter(eventLoopGroup, dispatcher)
          val createServerTest = new DefaultCreateServerTest(backend, interpreter)
          val ioSleeper: Sleeper[IO] = new Sleeper[IO] {
            override def sleep(duration: FiniteDuration): IO[Unit] = IO.sleep(duration)
          }

          val tests = new AllServerTests(
            createServerTest,
            interpreter,
            backend,
            multipart = false,
            maxContentLength = Some(NettyCatsTestServerInterpreter.maxContentLength)
          )
            .tests() ++
            new ServerStreamingTests(createServerTest, Fs2Streams[IO]).tests() ++
            new ServerCancellationTests(createServerTest)(m, IO.asyncForIO).tests() ++
            new NettyFs2StreamingCancellationTest(createServerTest).tests() ++
            new ServerGracefulShutdownTests(createServerTest, ioSleeper).tests()

          IO.pure((tests, eventLoopGroup))
        } { case (_, eventLoopGroup) =>
          IO.fromFuture(IO.delay(FutureUtil.nettyFutureToScala(eventLoopGroup.shutdownGracefully()): Future[_])).void
        }
        .map { case (tests, _) => tests }
    }
}
