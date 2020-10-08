package sttp.tapir.server.tests

import cats.effect.IO
import cats.syntax.all._
import sttp.capabilities.{Streams, WebSockets}
import sttp.client3._
import sttp.tapir._
import sttp.ws.{WebSocket, WebSocketFrame}
import sttp.tapir.json.circe._
import io.circe.generic.auto._
import sttp.tapir.tests.Fruit

trait ServerWebSocketTests[F[_], S, ROUTE] { this: ServerTests[F, S with WebSockets, ROUTE] =>
  val streams: Streams[S]

  def webSocketTests(): Unit = {
    testServer(endpoint.out(webSocketBody[String, String, CodecFormat.TextPlain].apply(streams)), "string client-terminated echo")(
      (_: Unit) => pureResult(functionToPipe((s: String) => s"echo: $s").asRight[Unit])
    ) { baseUri =>
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
    }

    testServer(endpoint.out(webSocketBody[Fruit, Fruit, CodecFormat.Json](streams)), "json client-terminated echo")((_: Unit) =>
      pureResult(functionToPipe((f: Fruit) => Fruit(s"echo: ${f.f}")).asRight[Unit])
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
    }

    testServer(endpoint.out(webSocketBody[String, Option[String], CodecFormat.TextPlain](streams)), "string server-terminated echo")(
      (_: Unit) =>
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
    }

    // TODO: tests for ping/pong (control frames handling)
  }

  def functionToPipe[A, B](f: A => B): streams.Pipe[A, B]
}
