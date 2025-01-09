package sttp.tapir.server.http4s

import cats.data._
import cats.effect._
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import fs2.Pipe
import fs2.Stream
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import org.http4s.server.ContextMiddleware
import org.http4s.ContextRoutes
import org.http4s.HttpRoutes
import org.scalatest.{Assertion, OptionValues}
import org.scalatest.matchers.should.Matchers._
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3._
import sttp.model.sse.ServerSentEvent
import sttp.tapir._
import sttp.tapir.integ.cats.effect.CatsMonadError
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}
import sttp.ws.{WebSocket, WebSocketFrame}

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.util.Random

class Http4sServerTest[R >: Fs2Streams[IO] with WebSockets] extends TestSuite with OptionValues {

  override def tests: Resource[IO, List[Test]] = backendResource.map { backend =>
    implicit val m: CatsMonadError[IO] = new CatsMonadError[IO]

    val interpreter = new Http4sTestServerInterpreter()
    val createServerTest = new DefaultCreateServerTest(backend, interpreter)
    def randomUUID = Some(UUID.randomUUID().toString)
    val sse1 = ServerSentEvent(randomUUID, randomUUID, randomUUID, Some(Random.nextInt(200)))
    val sse2 = ServerSentEvent(randomUUID, randomUUID, randomUUID, Some(Random.nextInt(200)))

    def assert_get_apiTestRouter_respondsWithExpectedContent[T](routes: HttpRoutes[IO], expectedContext: T): IO[Assertion] =
      BlazeServerBuilder[IO]
        .withExecutionContext(ExecutionContext.global)
        .bindHttp(0, "localhost")
        .withHttpApp(Router("/api" -> routes).orNotFound)
        .resource
        .use { server =>
          val port = server.address.getPort
          basicRequest.get(uri"http://localhost:$port/api/test/router").send(backend).map(_.body shouldBe Right(expectedContext))
        }

    def additionalTests(): List[Test] = List(
      Test("should work with a router and routes in a context") {
        val expectedContent: String = "ok"
        val e = endpoint.get.in("test" / "router").out(stringBody).serverLogic(_ => IO.pure(expectedContent.asRight[Unit]))
        val routes = Http4sServerInterpreter[IO]().toRoutes(e)

        assert_get_apiTestRouter_respondsWithExpectedContent(routes, expectedContent).unsafeRunSync()
      },
      Test("should work with a router and context routes in a context") {
        val expectedContext: String = "Hello World!" // the context we expect http4s to provide to the endpoint

        val e: Endpoint[Unit, String, Unit, String, Context[String]] =
          endpoint.get.in("test" / "router").contextIn[String]().out(stringBody)

        val routesWithContext: ContextRoutes[String, IO] =
          Http4sServerInterpreter[IO]()
            // server logic is to return the context as is
            .toContextRoutes(e.serverLogicSuccess(ctx => IO.pure(ctx)))

        // middleware to add the context to each request (so here string constant)
        val middleware: ContextMiddleware[IO, String] =
          ContextMiddleware.const(expectedContext)

        assert_get_apiTestRouter_respondsWithExpectedContent(middleware(routesWithContext), expectedContext).unsafeRunSync()
      },
      Test("should work with a router and context routes in a context using contextSecurityIn") {
        val expectedContext: Int = 3

        val e: Endpoint[Int, Unit, Unit, String, Context[Int]] =
          endpoint.get.in("test" / "router").contextSecurityIn[Int]().out(stringBody)

        val routesWithContext: ContextRoutes[Int, IO] =
          Http4sServerInterpreter[IO]()
            .toContextRoutes(e.serverSecurityLogicSuccess(IO.pure).serverLogicSuccess(x => _ => IO.pure(x.toString)))

        val middleware: ContextMiddleware[IO, Int] = ContextMiddleware.const(expectedContext)

        assert_get_apiTestRouter_respondsWithExpectedContent(middleware(routesWithContext), expectedContext.toString).unsafeRunSync()
      },
      createServerTest.testServer(
        endpoint.out(
          webSocketBody[String, CodecFormat.TextPlain, String, CodecFormat.TextPlain]
            .apply(Fs2Streams[IO])
            .autoPing(Some((1.second, WebSocketFrame.ping)))
        ),
        "automatic pings"
      )((_: Unit) => IO(Right((in: fs2.Stream[IO, String]) => in))) { (backend, baseUri) =>
        basicRequest
          .response(asWebSocket { (ws: WebSocket[IO]) =>
            List(ws.receive().timeout(60.seconds), ws.receive().timeout(60.seconds)).sequence
          })
          .get(baseUri.scheme("ws"))
          .send(backend)
          .map(_.body should matchPattern { case Right(List(WebSocketFrame.Ping(_), WebSocketFrame.Ping(_))) => })
      },
      createServerTest.testServer(
        endpoint.out(streamBinaryBody(Fs2Streams[IO])(CodecFormat.OctetStream())),
        "streaming should send data according to producer stream rate"
      )((_: Unit) =>
        IO(Right(fs2.Stream.awakeEvery[IO](1.second).map(_.toString()).through(fs2.text.utf8Encode).interruptAfter(10.seconds)))
      ) { (backend, baseUri) =>
        basicRequest
          .response(
            asStream(Fs2Streams[IO])(bs => {
              bs.through(fs2.text.utf8Decode).mapAccumulate(0)((pings, currentTime) => (pings + 1, currentTime)).compile.last
            })
          )
          .get(baseUri)
          .send(backend)
          .map(_.body match {
            case Right(Some((pings, _))) => pings should be >= 2
            case wrongResponse           => fail(s"expected to get count of received data chunks, instead got $wrongResponse")
          })
      },
      createServerTest.testServer(
        endpoint.out(serverSentEventsBody[IO]),
        "Send and receive SSE"
      )((_: Unit) => IO(Right(fs2.Stream(sse1, sse2)))) { (backend, baseUri) =>
        basicRequest
          .response(asStream[IO, List[ServerSentEvent], Fs2Streams[IO]](Fs2Streams[IO]) { stream =>
            Http4sServerSentEvents
              .parseBytesToSSE[IO]
              .apply(stream)
              .compile
              .toList
          })
          .get(baseUri)
          .send(backend)
          .map(_.body.right.toOption.value shouldBe List(sse1, sse2))
      }
    )

    def drainFs2(stream: Fs2Streams[IO]#BinaryStream): IO[Unit] =
      stream.compile.drain.void

    new AllServerTests(createServerTest, interpreter, backend).tests() ++
      new ServerStreamingTests(createServerTest).tests(Fs2Streams[IO])(drainFs2) ++
      new ServerWebSocketTests(
        createServerTest,
        Fs2Streams[IO],
        autoPing = true,
        handlePong = false
      ) {
        override def functionToPipe[A, B](f: A => B): streams.Pipe[A, B] = in => in.map(f)
        override def emptyPipe[A, B]: Pipe[IO, A, B] = _ => fs2.Stream.empty
      }.tests() ++
      additionalTests()
  }
}
