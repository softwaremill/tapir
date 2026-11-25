package sttp.tapir.server.netty.zio

import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Resource}
import io.netty.channel.nio.NioEventLoopGroup
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers._
import sttp.capabilities.zio.ZioStreams
import sttp.monad.MonadError
import sttp.tapir.endpoint
import sttp.tapir.server.netty.{NettyConfig, NettyServerStopTests}
import sttp.tapir.server.netty.internal.FutureUtil
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}
import sttp.tapir.ztapir.RIOMonadError
import zio.interop.catz._
import zio.{Runtime, Task, Unsafe, ZIO}

import java.util.concurrent.{CountDownLatch, TimeUnit}
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
              new ServerGracefulShutdownTests(createServerTest).tests() ++
              new NettyServerStopTests(eventLoopGroup) {
                override def withServer[A](config: NettyConfig)(use: IO[Unit] => IO[A]): IO[A] =
                  runZio(NettyZioServer[Any](config).start()).flatMap(server => use(runZio(server.stop())))
              }.tests() ++
              List(
                stopInterruptedTest(eventLoopGroup),
                startInterruptedTest
              )

          IO.pure((tests, eventLoopGroup))
        } { case (_, eventLoopGroup) =>
          IO.fromFuture(IO.delay(FutureUtil.nettyFutureToScala(eventLoopGroup.shutdownGracefully()): Future[?])).void
        }
        .map { case (tests, _) => tests }
    }

  // not IO.fromFuture, which can't be timed out
  private def runZio[A](z: Task[A]): IO[A] =
    IO.blocking(Await.result(Unsafe.unsafe(implicit u => Runtime.default.unsafe.runToFuture(z)), 20.seconds))

  private def stopInterruptedTest(eventLoopGroup: NioEventLoopGroup): Test =
    Test("interrupting stop() still stops the server, and later stop() calls succeed") {
      val requestReceived = new CountDownLatch(1)
      val releaseRequest = new CountDownLatch(1)
      val blocking = endpoint.serverLogicSuccess[Task] { _ =>
        ZIO.succeed(requestReceived.countDown()) *> ZIO.attemptBlocking(releaseRequest.await(30, TimeUnit.SECONDS)).unit
      }
      val config = NettyConfig.default
        .eventLoopGroup(eventLoopGroup)
        .randomPort
        .withDontShutdownEventLoopGroupOnClose
        .withGracefulShutdownTimeout(1.second)
      runZio(NettyZioServer[Any](config).addEndpoints(List(blocking)).start())
        .flatMap { binding =>
          rawSocket(binding.port).use { socket =>
            for {
              _ <- sendRaw(socket, rawGet())
              requestArrived <- IO.blocking(requestReceived.await(10, TimeUnit.SECONDS))
              firstStop <- runZio(binding.stop().forkDaemon)
              // the request keeps the server stopping until the graceful shutdown timeout, and then the connection is closed
              _ <- awaitConnectionsRefused(binding.port).timeout(5.seconds)
              _ <- runZio(firstStop.interrupt)
              requestConnectionClosed <- IO.blocking(socket.getInputStream.read() == -1)
              secondStop <- runZio(binding.stop().timeout(zio.Duration.fromSeconds(5)))
            } yield (requestArrived, requestConnectionClosed, secondStop) shouldBe ((true, true, Some(())))
          }
        }
        .guarantee(IO(releaseRequest.countDown()))
        .unsafeToFuture()
    }

  private def startInterruptedTest: Test =
    Test("interrupting start() shuts down the event loop group") {
      // blocked, so that the server isn't bound before start() is interrupted
      val eventLoopGroup = new NioEventLoopGroup(1)
      val unblock = new CountDownLatch(1)
      eventLoopGroup.execute(() => unblock.await())
      val config = NettyConfig.default.eventLoopGroup(eventLoopGroup).randomPort
      val test = for {
        starting <- NettyZioServer[Any](config).start().timeout(zio.Duration.fromMillis(500)).fork
        _ <- ZIO.sleep(zio.Duration.fromSeconds(1)) // the timeout passes
        _ <- ZIO.succeed(unblock.countDown())
        started <- starting.join
        terminated <- ZIO.attemptBlocking(eventLoopGroup.awaitTermination(5, TimeUnit.SECONDS))
      } yield (started.isEmpty, terminated) shouldBe ((true, true))
      runZio(test)
        .guarantee(IO(unblock.countDown()) >> IO(eventLoopGroup.shutdownGracefully()).void)
        .unsafeToFuture()
    }
}
