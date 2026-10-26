package sttp.tapir.server.netty.sync

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
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import ox.flow.Flow
import scala.annotation.nowarn
import sttp.tapir.server.netty.{NettyConfig, NettySyncRequestTimeoutTests}
import sttp.model.sse.ServerSentEvent
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.{CountDownLatch, TimeUnit}
import scala.util.Random
import sttp.tapir.server.netty.OxServerSentEvents.parseBytesToSSE

class NettySyncServerTest extends AsyncFunSuite with BeforeAndAfterAll {

  val (backend, stopBackend) = backendResource.allocated.unsafeRunSync()
  def testNameFilter: Option[String] = None // define to run a single test (temporarily for debugging)
  {
    @scala.annotation.nowarn
    val eventLoopGroup = new NioEventLoopGroup()

    val interpreter = new NettySyncTestServerInterpreter(eventLoopGroup)
    val createServerTest = new NettySyncCreateServerTest(backend, interpreter)
    // with interruption (the default), request processing is cancelled before a truncated body could be decoded
    val nonInterruptingCreateServerTest = new NettySyncCreateServerTest(
      backend,
      new NettySyncTestServerInterpreter(eventLoopGroup, interruptServerLogicWhenRequestCancelled = false)
    )
    val sleeper: Sleeper[Identity] = (duration: FiniteDuration) => Thread.sleep(duration.toMillis)

    val tests =
      new AllServerTests(createServerTest, interpreter, backend, staticContent = false, multipart = false)
        .tests() ++
        new ServerGracefulShutdownTests(createServerTest, sleeper).tests() ++
        new ServerStreamingTests(createServerTest).tests(OxStreams)(_.runDrain()) ++
        new ServerWebSocketTests(createServerTest, OxStreams, autoPing = true, handlePong = true) {
          override def functionToPipe[A, B](f: A => B): OxStreams.Pipe[A, B] = _.map(f)
          override def emptyPipe[A, B]: OxStreams.Pipe[A, B] = _ => Flow.empty
        }.tests() ++
        new ServerMultipartTests(createServerTest, partOtherHeaderSupport = false).tests() ++
        new ServerIncompleteRequestBodyTests(nonInterruptingCreateServerTest).tests() ++
        NettySyncRequestTimeoutTests(eventLoopGroup, backend).tests() ++
        additionalTests(createServerTest) ++
        expectContinueTests(createServerTest, eventLoopGroup)

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

  def additionalTests(createServerTest: CreateServerTest[Identity, OxStreams & WebSockets, NettySyncServerOptions, IdRoute]): List[Test] =
    List(
      {
        def randomUUID = Some(UUID.randomUUID().toString)
        val sse1 = ServerSentEvent(randomUUID, randomUUID, randomUUID, Some(Random.nextInt(200)))
        val sse2 = ServerSentEvent(randomUUID, randomUUID, randomUUID, Some(Random.nextInt(200)))
        createServerTest.testServerLogic(
          endpoint.get.in("sse").out(serverSentEventsBody).handleSuccess(_ => Flow.fromIterable(List(sse1, sse2)))
        ) { (backend, baseUri) =>
          basicRequest
            .get(uri"$baseUri/sse")
            .response(asByteArrayAlways)
            .send(backend)
            .map(_.body)
            .map { bytes =>
              parseBytesToSSE(Flow.fromValues(Chunk.fromArray(bytes))).runToList() shouldBe List(sse1, sse2)
            }
        }
      },
      createServerTest.testServerLogic(
        endpoint.get.in("hello").out(stringBody).handleSuccess(_ => "ok"),
        testNameSuffix = "properly log invalid requests when the URL is malformed"
      ) { (_, baseUri) =>
        IO.blocking {
          @scala.annotation.nowarn
          val conn = new java.net.URL(s"$baseUri/hello?param=%%2G").openConnection().asInstanceOf[java.net.HttpURLConnection]
          try conn.getResponseCode() shouldBe 400
          finally conn.disconnect
        }
      },
      // #5150: large InputStream responses previously caused StackOverflowError due to recursive readNextChunkIfNeeded
      createServerTest.testServerLogic(
        endpoint.get
          .in("large-stream")
          .out(inputStreamBody)
          .handleSuccess(_ => new java.io.ByteArrayInputStream(new Array[Byte](10 * 1024 * 1024))),
        testNameSuffix = "large InputStream response should not cause StackOverflowError"
      ) { (backend, baseUri) =>
        basicRequest
          .get(uri"$baseUri/large-stream")
          .response(asByteArrayAlways)
          .send(backend)
          .map(_.body.length shouldBe 10 * 1024 * 1024)
      }
    )

  /** Netty-only: RFC 9110 doesn't specify when exactly a 100 is sent with pipelining, or for a body that might be read later, and servers
    * differ. These test `TapirHttpStreamsServerHandler`, which is the same for all Netty interpreters, so they're run once, here, as they
    * need streamed bodies.
    */
  def expectContinueTests(
      createServerTest: CreateServerTest[Identity, OxStreams & WebSockets, NettySyncServerOptions, IdRoute],
      eventLoopGroup: NioEventLoopGroup
  ): List[Test] = {
    // not using `interpreter.route`, which creates the route in a scope which is already closed when streaming responses
    def testServer(name: String, es: List[ServerEndpoint[OxStreams & WebSockets, Identity]])(runTest: Int => IO[Assertion]): Test =
      Test(name) {
        supervised {
          val config =
            NettyConfig.default.eventLoopGroup(eventLoopGroup).randomPort.withDontShutdownEventLoopGroupOnClose.noGracefulShutdown
          val binding = useInScope(NettySyncServer(config).addEndpoints(es).start())(_.stop())
          Future.successful(runTest(binding.port).unsafeRunSync())
        }
      }
    def chunk(s: String): Chunk[Byte] = Chunk.fromArray(s.getBytes(StandardCharsets.UTF_8))
    def await(latch: CountDownLatch): Unit = { val _ = latch.await(5, TimeUnit.SECONDS) }
    val textStream = streamBinaryBody(OxStreams)(CodecFormat.TextPlain())

    List(
      {
        val secondLogicStarted = new CountDownLatch(1)
        val firstResponseRelease = new CountDownLatch(1)

        // a known length, so that the connection is kept open after the response
        val first = endpoint.get
          .in("first")
          .out(header(HeaderNames.ContentLength, "2"))
          .out(textStream)
          .handleSuccess(_ =>
            Flow.usingEmit { emit =>
              emit(chunk("a"))
              await(firstResponseRelease)
              emit(chunk("b"))
            }
          )
        val echo = endpoint.post
          .in("echo")
          .in(textStream)
          .out(textStream)
          .handleSuccess { body =>
            secondLogicStarted.countDown()
            body
          }

        testServer("send 100 Continue for a pipelined request only after the preceding response", List(first, echo)) { port =>
          val firstRequest = s"GET /first HTTP/1.1\r\nHost: localhost:$port\r\n\r\n"
          val secondBody = "Sweet"
          val secondRequestHead =
            s"POST /echo HTTP/1.1\r\nHost: localhost:$port\r\nContent-Length: ${secondBody.length}\r\nExpect: 100-continue\r\n\r\n"

          rawSocket(port).use { socket =>
            for {
              _ <- sendRaw(socket, (firstRequest + secondRequestHead).getBytes(StandardCharsets.UTF_8))
              // giving the server time to handle the second request, while the first response is still being written
              _ <- IO.blocking(await(secondLogicStarted)) >> IO.sleep(500.millis) >> IO(firstResponseRelease.countDown())
              firstResponse <- readRawResponse(socket)
              continue <- readRawResponse(socket)
              _ <- sendRaw(socket, secondBody.getBytes(StandardCharsets.UTF_8))
              secondResponse <- readRawResponse(socket)
            } yield List(firstResponse, continue, secondResponse) shouldBe List((200, "ab"), (100, ""), (200, secondBody))
          }
        }
      }, {
        val secondLogicStarted = new CountDownLatch(1)

        // the body is not read, as otherwise `HttpStreamsServerHandler` sends a 100 for the newest request
        val first = endpoint.post
          .in("first")
          .in(textStream)
          .out(header(HeaderNames.ContentLength, "2")) // a known length, so that the connection is kept open after the response
          .out(textStream)
          // writing the response once the second request is handled, as only then a 100 might be sent for it
          .handleSuccess { _ =>
            await(secondLogicStarted)
            Flow.fromValues(chunk("ab"))
          }
        val second = endpoint.post
          .in("second")
          .out(textStream)
          .handleSuccess { _ =>
            secondLogicStarted.countDown()
            Flow.fromValues(chunk("ok"))
          }

        testServer(
          "don't send 100 Continue for a pipelined request because a preceding one might read its body after the response headers",
          List(first, second)
        ) { port =>
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
      },
      createServerTest.testServerLogic(
        endpoint.post.in("expect").in(textStream).out(stringBody).handleSuccess(_ => "ok"),
        "respond without 100 Continue, and close the connection, if a streamed request body is not read before a complete response"
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
    )
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

  override def testServerWithStop(name: String, r: => IdRoute, gracefulShutdownTimeout: Option[FiniteDuration])(
      runTest: IO[Unit] => (WebSocketStreamBackend[IO, Fs2Streams[IO]], Uri) => IO[Assertion]
  ): Test = throw new UnsupportedOperationException

  override def testServer(name: String, r: => IdRoute)(
      runTest: (WebSocketStreamBackend[IO, Fs2Streams[IO]], Uri) => IO[Assertion]
  ): Test =
    Test(name) {
      supervised {
        val binding = interpreter.scopedServerWithRouteStop(r)
        val assertion: Assertion =
          runTest(backend, uri"http://localhost:${binding.port}")
            .guarantee(IO(logger.info(s"Test completed on port ${binding.port}")))
            .unsafeRunSync()
        Future.successful(assertion)
      }
    }
}
