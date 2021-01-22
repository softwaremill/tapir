package sttp.tapir.server.http4s

import cats.effect._
import cats.syntax.all._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.syntax.kleisli._
import org.scalatest.matchers.should.Matchers._
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3._
import sttp.tapir._
import sttp.tapir.server.tests.{
  ServerAuthenticationTests,
  ServerBasicTests,
  ServerEffectfulMappingsTests,
  ServerStreamingTests,
  ServerTests,
  ServerWebSocketTests,
  backendResource
}
import sttp.tapir.tests.{Test, TestSuite}
import sttp.ws.{WebSocket, WebSocketFrame}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

class Http4sServerTests[R >: Fs2Streams[IO] with WebSockets] extends TestSuite {

  override def tests: Resource[IO, List[Test]] = backendResource.map { backend =>
    implicit val m: CatsMonadError[IO] = new CatsMonadError[IO]
    val interpreter = new Http4sTestServerInterpreter()
    val serverTests = new ServerTests(interpreter)

    import interpreter.timer

    def additionalTests(): List[Test] = List(
      Test("should work with a router and routes in a context") {
        val e = endpoint.get.in("test" / "router").out(stringBody).serverLogic(_ => IO.pure("ok".asRight[Unit]))
        val routes = Http4sServerInterpreter.toRoutes(e)

        BlazeServerBuilder[IO](ExecutionContext.global)
          .bindHttp(0, "localhost")
          .withHttpApp(Router("/api" -> routes).orNotFound)
          .resource
          .use { server =>
            val port = server.address.getPort
            basicRequest.get(uri"http://localhost:$port/api/test/router").send(backend).map(_.body shouldBe Right("ok"))
          }
          .unsafeRunSync()
      },
      serverTests.testServer(
        endpoint.out(
          webSocketBody[String, CodecFormat.TextPlain, String, CodecFormat.TextPlain]
            .apply(Fs2Streams[IO])
            .autoPing(Some((1.second, WebSocketFrame.ping)))
        ),
        "automatic pings"
      )((_: Unit) => IO(Right((in: fs2.Stream[IO, String]) => in))) { baseUri =>
        basicRequest
          .response(asWebSocket { ws: WebSocket[IO] =>
            List(ws.receive().timeout(60.seconds), ws.receive().timeout(60.seconds)).sequence
          })
          .get(baseUri.scheme("ws"))
          .send(backend)
          .map(_.body should matchPattern { case Right(List(WebSocketFrame.Ping(_), WebSocketFrame.Ping(_))) => })
      }
    )

    new ServerBasicTests(backend, serverTests, interpreter).tests() ++
      new ServerStreamingTests(backend, serverTests, Fs2Streams[IO]).tests() ++
      new ServerWebSocketTests(backend, serverTests, Fs2Streams[IO]) {
        override def functionToPipe[A, B](f: A => B): streams.Pipe[A, B] = in => in.map(f)
      }.tests() ++
      new ServerAuthenticationTests(backend, serverTests).tests() ++
      new ServerEffectfulMappingsTests(backend, serverTests).tests() ++
      additionalTests()
  }
}
