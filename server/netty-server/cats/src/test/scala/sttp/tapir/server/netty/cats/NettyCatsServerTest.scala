package sttp.tapir.server.netty.cats

import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Resource}
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers._
import sttp.capabilities.fs2.Fs2Streams
import sttp.monad.MonadError
import sttp.tapir.endpoint
import sttp.tapir.integ.cats.effect.CatsMonadError
import sttp.tapir.server.netty.{NettyConfig, NettyServerStopTests}
import sttp.tapir.server.netty.NettyConfig.EventLoopConfig
import sttp.tapir.server.netty.internal.FutureUtil
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{CountDownLatch, TimeUnit}
import scala.concurrent.Future
import scala.concurrent.duration._

class NettyCatsServerTest extends TestSuite with EitherValues {

  override def tests: Resource[IO, List[Test]] =
    backendResource.flatMap { backend =>
      Resource
        .make {
          implicit val m: MonadError[IO] = new CatsMonadError[IO]()
          val eventLoopGroup = new NioEventLoopGroup()

          val interpreter = new NettyCatsTestServerInterpreter(eventLoopGroup, dispatcher)
          val createServerTest = new DefaultCreateServerTest(backend, interpreter)
          def drainFs2(stream: Fs2Streams[IO]#BinaryStream): IO[Unit] =
            stream.compile.drain.void

          val webSocketTests = new ServerWebSocketTests(createServerTest, Fs2Streams[IO], autoPing = true, handlePong = true) {
            override def functionToPipe[A, B](f: A => B): streams.Pipe[A, B] = in => in.map(f)
            override def emptyPipe[A, B]: fs2.Pipe[IO, A, B] = _ => fs2.Stream.empty
          }

          val tests = new AllServerTests(
            createServerTest,
            interpreter,
            backend,
            partOtherHeaderSupport = false
          )
            .tests() ++
            new ServerStreamingTests(createServerTest).tests(Fs2Streams[IO])(drainFs2) ++
            new ServerCancellationTests(createServerTest)(using m, IO.asyncForIO).tests() ++
            new NettyFs2StreamingCancellationTest(createServerTest).tests() ++
            new NettyFs2StreamingInterruptedTest(createServerTest).tests() ++
            new ServerGracefulShutdownTests(createServerTest).tests() ++
            webSocketTests.tests() ++
            webSocketTests.gracefulShutdownTests(_ => fs2.Stream.awakeEvery[IO](100.millis).as("tick")) ++
            webSocketTests.unsupportedVersionTests() ++
            new NettyServerStopTests(eventLoopGroup) {
              override def withServer[A](config: NettyConfig)(use: IO[Unit] => IO[A]): IO[A] =
                NettyCatsServer[IO](dispatcher, config)
                  .start()
                  .timeout(10.seconds)
                  .flatMap(server => use(server.stop().timeout(10.seconds)))
            }.tests() ++
            List(
              stopCancelledTest(eventLoopGroup),
              startCancelledTest,
              startLazyTest(eventLoopGroup)
            )

          IO.pure((tests, eventLoopGroup))
        } { case (_, eventLoopGroup) =>
          IO.fromFuture(IO.delay(FutureUtil.nettyFutureToScala(eventLoopGroup.shutdownGracefully()): Future[?])).void
        }
        .map { case (tests, _) => tests }
    }

  private def stopCancelledTest(eventLoopGroup: NioEventLoopGroup): Test =
    Test("cancelling stop() still stops the server, and later stop() calls succeed") {
      val requestReceived = new CountDownLatch(1)
      val releaseRequest = new CountDownLatch(1)
      val blocking = endpoint.serverLogicSuccess[IO] { _ =>
        IO(requestReceived.countDown()) >> IO.blocking(releaseRequest.await(30, TimeUnit.SECONDS)).void
      }
      val config = NettyConfig.default
        .eventLoopGroup(eventLoopGroup)
        .randomPort
        .withDontShutdownEventLoopGroupOnClose
        .withGracefulShutdownTimeout(1.second)
      NettyCatsServer[IO](dispatcher, config)
        .addEndpoint(blocking)
        .start()
        .flatMap { binding =>
          rawSocket(binding.port).use { socket =>
            for {
              _ <- sendRaw(socket, rawGet())
              requestArrived <- IO.blocking(requestReceived.await(10, TimeUnit.SECONDS))
              firstStop <- binding.stop().start
              // the request keeps the server stopping until the graceful shutdown timeout, and then the connection is closed
              _ <- awaitConnectionsRefused(binding.port).timeout(5.seconds)
              _ <- firstStop.cancel
              requestConnectionClosed <- IO.blocking(socket.getInputStream.read() == -1)
              secondStop <- binding.stop().timeout(5.seconds).attempt
            } yield (requestArrived, requestConnectionClosed, secondStop) shouldBe ((true, true, Right(())))
          }
        }
        .guarantee(IO(releaseRequest.countDown()))
        .unsafeToFuture()
    }

  private def startLazyTest(eventLoopGroup: NioEventLoopGroup): Test =
    Test("start() doesn't start a server until the returned effect is run") {
      val eventLoopGroupInits = new AtomicInteger(0)
      val config = NettyConfig.default.randomPort.withDontShutdownEventLoopGroupOnClose
        .eventLoopConfig(EventLoopConfig(() => { eventLoopGroupInits.incrementAndGet(); eventLoopGroup }, classOf[NioServerSocketChannel]))
      val start = NettyCatsServer[IO](dispatcher, config).start()
      (for {
        initsBeforeRun <- IO(eventLoopGroupInits.get())
        _ <- start.flatMap(_.stop())
      } yield (initsBeforeRun, eventLoopGroupInits.get()) shouldBe ((0, 1))).unsafeToFuture()
    }

  private def startCancelledTest: Test =
    Test("cancelling start() shuts down the event loop group") {
      // blocked, so that the server isn't bound before start() is cancelled
      val eventLoopGroup = new NioEventLoopGroup(1)
      val unblock = new CountDownLatch(1)
      eventLoopGroup.execute(() => unblock.await())
      val config = NettyConfig.default.eventLoopGroup(eventLoopGroup).randomPort
      (for {
        starting <- NettyCatsServer[IO](dispatcher, config).start().timeout(500.millis).attempt.start
        _ <- IO.sleep(1.second) // the timeout passes
        _ <- IO(unblock.countDown())
        started <- starting.joinWithNever
        terminated <- IO.blocking(eventLoopGroup.awaitTermination(5, TimeUnit.SECONDS))
      } yield (started.isLeft, terminated) shouldBe ((true, true)))
        .guarantee(IO(unblock.countDown()) >> IO(eventLoopGroup.shutdownGracefully()).void)
        .unsafeToFuture()
    }
}
