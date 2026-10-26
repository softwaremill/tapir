package sttp.tapir.server.netty.cats

import cats.effect.{Deferred, IO, Resource}
import io.netty.channel.nio.NioEventLoopGroup
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers._
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.HeaderNames
import sttp.monad.MonadError
import sttp.tapir._
import sttp.tapir.integ.cats.effect.CatsMonadError
import sttp.tapir.server.netty.Route
import sttp.tapir.server.netty.internal.FutureUtil
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}

import java.nio.charset.StandardCharsets
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
          val ioSleeper: Sleeper[IO] = new Sleeper[IO] {
            override def sleep(duration: FiniteDuration): IO[Unit] = IO.sleep(duration)
          }
          def drainFs2(stream: Fs2Streams[IO]#BinaryStream): IO[Unit] =
            stream.compile.drain.void

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
            new ServerGracefulShutdownTests(createServerTest, ioSleeper).tests() ++
            new ServerWebSocketTests(
              createServerTest,
              Fs2Streams[IO],
              autoPing = true,
              handlePong = true
            ) {
              override def functionToPipe[A, B](f: A => B): streams.Pipe[A, B] = in => in.map(f)
              override def emptyPipe[A, B]: fs2.Pipe[IO, A, B] = _ => fs2.Stream.empty
            }.tests() ++
            List(
              pipelinedExpectContinueTest(createServerTest, interpreter),
              pipelinedNoExpectContinueTest(createServerTest, interpreter),
              noExpectContinueForFullResponseTest(createServerTest, interpreter)
            )

          IO.pure((tests, eventLoopGroup))
        } { case (_, eventLoopGroup) =>
          IO.fromFuture(IO.delay(FutureUtil.nettyFutureToScala(eventLoopGroup.shutdownGracefully()): Future[?])).void
        }
        .map { case (tests, _) => tests }
    }

  private def pipelinedExpectContinueTest(
      createServerTest: CreateServerTest[IO, Any, NettyCatsServerOptions[IO], Route[IO]],
      interpreter: NettyCatsTestServerInterpreter
  ): Test = {
    val secondLogicStarted = Deferred.unsafe[IO, Unit]
    val firstResponseRelease = Deferred.unsafe[IO, Unit]

    // a known length, so that the connection is kept open after the response
    val first = endpoint.get
      .in("first")
      .out(header(HeaderNames.ContentLength, "2"))
      .out(streamBinaryBody(Fs2Streams[IO])(CodecFormat.TextPlain()))
      .serverLogicSuccess[IO](_ =>
        IO.pure(fs2.Stream.emit('a'.toByte) ++ fs2.Stream.exec(firstResponseRelease.get) ++ fs2.Stream.emit('b'.toByte))
      )
    val echo = endpoint.post
      .in("echo")
      .in(streamBinaryBody(Fs2Streams[IO])(CodecFormat.TextPlain()))
      .out(streamBinaryBody(Fs2Streams[IO])(CodecFormat.TextPlain()))
      .serverLogicSuccess[IO](body => secondLogicStarted.complete(()).as(body))

    createServerTest.testServer(
      "send 100 Continue for a pipelined request only after the preceding response",
      interpreter.route(List(first, echo))
    ) { (_, baseUri) =>
      val port = baseUri.port.get
      val firstRequest = s"GET /first HTTP/1.1\r\nHost: localhost:$port\r\n\r\n"
      val secondBody = "Sweet"
      val secondRequestHead =
        s"POST /echo HTTP/1.1\r\nHost: localhost:$port\r\nContent-Length: ${secondBody.length}\r\nExpect: 100-continue\r\n\r\n"

      rawSocket(port).use { socket =>
        for {
          _ <- sendRaw(socket, (firstRequest + secondRequestHead).getBytes(StandardCharsets.UTF_8))
          // giving the server time to handle the second request, while the first response is still being written
          _ <- secondLogicStarted.get.timeout(5.seconds) >> IO.sleep(500.millis) >> firstResponseRelease.complete(())
          firstResponse <- readRawResponse(socket)
          continue <- readRawResponse(socket)
          _ <- sendRaw(socket, secondBody.getBytes(StandardCharsets.UTF_8))
          secondResponse <- readRawResponse(socket)
        } yield List(firstResponse, continue, secondResponse) shouldBe List((200, "ab"), (100, ""), (200, secondBody))
      }
    }
  }

  private def pipelinedNoExpectContinueTest(
      createServerTest: CreateServerTest[IO, Any, NettyCatsServerOptions[IO], Route[IO]],
      interpreter: NettyCatsTestServerInterpreter
  ): Test = {
    val secondLogicStarted = Deferred.unsafe[IO, Unit]

    // the body is not read, as otherwise the library sends a 100 for the newest request
    val first = endpoint.post
      .in("first")
      .in(streamBinaryBody(Fs2Streams[IO])(CodecFormat.TextPlain()))
      .out(header(HeaderNames.ContentLength, "2")) // a known length, so that the connection is kept open after the response
      .out(streamBinaryBody(Fs2Streams[IO])(CodecFormat.TextPlain()))
      // writing the response once the second request is handled, as only then a 100 might be sent for it
      .serverLogicSuccess[IO](_ => secondLogicStarted.get.timeout(5.seconds).as(fs2.Stream.emits("ab".getBytes(StandardCharsets.UTF_8))))
    val second = endpoint.post
      .in("second")
      .out(streamBinaryBody(Fs2Streams[IO])(CodecFormat.TextPlain()))
      .serverLogicSuccess[IO](_ => secondLogicStarted.complete(()).as(fs2.Stream.emits("ok".getBytes(StandardCharsets.UTF_8))))

    createServerTest.testServer(
      "don't send 100 Continue for a pipelined request because a preceding one might read its body after the response headers",
      interpreter.route(List(first, second))
    ) { (_, baseUri) =>
      val port = baseUri.port.get
      val firstRequest = s"POST /first HTTP/1.1\r\nHost: localhost:$port\r\nContent-Length: 5\r\n\r\nSweet"
      val secondRequestHead = s"POST /second HTTP/1.1\r\nHost: localhost:$port\r\nContent-Length: 4\r\nExpect: 100-continue\r\n\r\n"

      rawSocket(port).use { socket =>
        for {
          _ <- sendRaw(socket, (firstRequest + secondRequestHead).getBytes(StandardCharsets.UTF_8))
          firstResponse <- readRawResponse(socket)
          secondResponse <- readRawResponse(socket)
          connectionClosed <- IO.blocking(socket.getInputStream.read() == -1)
        } yield (firstResponse, secondResponse, connectionClosed) shouldBe (((200, "ab"), (200, "ok"), true))
      }
    }
  }

  private def noExpectContinueForFullResponseTest(
      createServerTest: CreateServerTest[IO, Any, NettyCatsServerOptions[IO], Route[IO]],
      interpreter: NettyCatsTestServerInterpreter
  ): Test = {
    val e = endpoint.post
      .in("expect")
      .in(streamBinaryBody(Fs2Streams[IO])(CodecFormat.TextPlain()))
      .out(stringBody)
      .serverLogicSuccess[IO](_ => IO.pure("ok"))

    createServerTest.testServer(
      "respond without 100 Continue, and close the connection, if a streamed request body is not read before a complete response",
      interpreter.route(List(e))
    ) { (_, baseUri) =>
      val port = baseUri.port.get
      val requestHead = s"POST /expect HTTP/1.1\r\nHost: localhost:$port\r\nContent-Length: 4\r\nExpect: 100-continue\r\n\r\n"
      rawSocket(port).use { socket =>
        for {
          _ <- sendRaw(socket, requestHead.getBytes(StandardCharsets.UTF_8))
          response <- readRawResponse(socket)
          connectionClosed <- IO.blocking(socket.getInputStream.read() == -1)
        } yield (response, connectionClosed) shouldBe (((200, "ok"), true))
      }
    }
  }
}
