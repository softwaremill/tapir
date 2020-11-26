package sttp.tapir.server.tests

import cats.effect.IO
import cats.syntax.all._
import sttp.capabilities.{Streams, WebSockets}
import sttp.client3._
import sttp.tapir._
import sttp.ws.{WebSocket, WebSocketFrame}
import sttp.tapir.json.circe._
import sttp.tapir.generic.auto._
import io.circe.generic.auto._
import sttp.monad.MonadError
import sttp.tapir.tests.{Fruit, Test}
import org.scalatest.matchers.should.Matchers._
import sttp.capabilities.fs2.Fs2Streams

abstract class ServerWebSocketTests[F[_], S <: Streams[S], ROUTE](
    backend: SttpBackend[IO, Fs2Streams[IO] with WebSockets],
    serverTests: ServerTests[F, S with WebSockets, ROUTE],
    val streams: S
)(implicit
    m: MonadError[F]
) {
  import serverTests._

  private def pureResult[T](t: T): F[T] = m.unit(t)
  def functionToPipe[A, B](f: A => B): streams.Pipe[A, B]

  private def stringWs = webSocketBody[String, CodecFormat.TextPlain, String, CodecFormat.TextPlain].apply(streams)
  private def stringEcho = functionToPipe((s: String) => s"echo: $s")

  def tests(): List[Test] = List(
    testServer(
      endpoint.out(stringWs),
      "string client-terminated echo"
    )((_: Unit) => pureResult(stringEcho.asRight[Unit])) { baseUri =>
      basicRequest
        .response(asWebSocket { ws: WebSocket[IO] =>
          for {
            _ <- ws.sendText("test1")
            _ <- ws.sendText("test2")
            m1 <- ws.receiveText()
            m2 <- ws.receiveText()
          } yield List(m1, m2)
        })
        .get(baseUri.scheme("ws"))
        .send(backend)
        .map(_.body shouldBe Right(List(Right("echo: test1"), Right("echo: test2"))))
    },
    testServer(endpoint.out(webSocketBody[Fruit, CodecFormat.Json, Fruit, CodecFormat.Json](streams)), "json client-terminated echo")(
      (_: Unit) => pureResult(functionToPipe((f: Fruit) => Fruit(s"echo: ${f.f}")).asRight[Unit])
    ) { baseUri =>
      basicRequest
        .response(asWebSocket { ws: WebSocket[IO] =>
          for {
            _ <- ws.sendText("""{"f":"apple"}""")
            _ <- ws.sendText("""{"f":"orange"}""")
            m1 <- ws.receiveText()
            m2 <- ws.receiveText()
          } yield List(m1, m2)
        })
        .get(baseUri.scheme("ws"))
        .send(backend)
        .map(_.body shouldBe Right(List(Right("""{"f":"echo: apple"}"""), Right("""{"f":"echo: orange"}"""))))
    },
    testServer(
      endpoint.out(webSocketBody[String, CodecFormat.TextPlain, Option[String], CodecFormat.TextPlain](streams)),
      "string server-terminated echo"
    )((_: Unit) =>
      pureResult(functionToPipe[String, Option[String]] {
        case "end" => None
        case msg   => Some(s"echo: $msg")
      }.asRight[Unit])
    ) { baseUri =>
      basicRequest
        .response(asWebSocket { ws: WebSocket[IO] =>
          for {
            _ <- ws.sendText("test1")
            _ <- ws.sendText("test2")
            _ <- ws.sendText("end")
            m1 <- ws.receiveText()
            m2 <- ws.receiveText()
            m3 <- ws.receiveText()
          } yield List(m1, m2, m3)
        })
        .get(baseUri.scheme("ws"))
        .send(backend)
        .map(
          _.body.map(_.map(_.left.map(_.statusCode))) shouldBe Right(
            List(Right("echo: test1"), Right("echo: test2"), Left(WebSocketFrame.close.statusCode))
          )
        )
    },
    testServer(
      endpoint
        .in(isWebSocket)
        .errorOut(stringBody)
        .out(stringWs),
      "non web-socket request"
    )(isWS => if (isWS) pureResult(stringEcho.asRight) else pureResult("Not a WS!".asLeft)) { baseUri =>
      basicRequest
        .response(asString)
        .get(baseUri.scheme("http"))
        .send(backend)
        .map(_.body shouldBe Left("Not a WS!"))
    }
  )

  // TODO: tests for ping/pong (control frames handling)
}
