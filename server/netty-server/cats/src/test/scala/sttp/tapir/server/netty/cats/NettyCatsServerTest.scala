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
          def drainFs2(stream: Fs2Streams[IO]#BinaryStream): IO[Unit] =
            stream.compile.drain.void

          val tests = new AllServerTests(
            createServerTest,
            interpreter,
            backend,
            multipart = false
          )
            .tests() ++
            new ServerStreamingTests(createServerTest).tests(Fs2Streams[IO])(drainFs2) ++
            new ServerCancellationTests(createServerTest)(m, IO.asyncForIO).tests() ++
            new NettyFs2StreamingCancellationTest(createServerTest).tests() ++
            new ServerGracefulShutdownTests(createServerTest, ioSleeper).tests() ++
            new ServerWebSocketTests(
              createServerTest,
              Fs2Streams[IO],
              autoPing = true,
              failingPipe = true,
              handlePong = true,
              rejectNonWsEndpoints = true
            ) {
              override def functionToPipe[A, B](f: A => B): streams.Pipe[A, B] = in => in.map(f)
              override def emptyPipe[A, B]: fs2.Pipe[IO, A, B] = _ => fs2.Stream.empty
            }.tests()

          IO.pure((tests, eventLoopGroup))
        } { case (_, eventLoopGroup) =>
          IO.fromFuture(IO.delay(FutureUtil.nettyFutureToScala(eventLoopGroup.shutdownGracefully()): Future[_])).void
        }
        .map { case (tests, _) => tests }
    }
}
