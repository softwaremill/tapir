package sttp.tapir.client.tests

import cats.effect.IO
import sttp.capabilities.{Streams, WebSockets}
import sttp.tapir._
import sttp.tapir.json.circe._
import io.circe.generic.auto._
import sttp.tapir.generic.auto._
import sttp.tapir.tests.data.Fruit
import sttp.ws.WebSocketFrame

trait ClientWebSocketTests[S] { this: ClientTests[S with WebSockets] =>
  val streams: Streams[S]

  def sendAndReceiveLimited[A, B](p: streams.Pipe[A, B], receiveCount: Int, as: List[A]): IO[List[B]]

  def webSocketTests(): Unit = {
    test("web sockets, string client-terminated echo") {
      send(
        endpoint.get.in("ws" / "echo").out(webSocketBody[String, CodecFormat.TextPlain, String, CodecFormat.TextPlain].apply(streams)),
        port,
        (),
        (),
        "ws"
      )
        .flatMap { r =>
          sendAndReceiveLimited(r.toOption.get, 2, List("test1", "test2"))
        }
        .map(_ shouldBe List("echo: test1", "echo: test2"))
        .unsafeToFuture()
    }

    test("web sockets, json client-terminated echo") {
      send(
        endpoint.get.in("ws" / "echo").out(webSocketBody[Fruit, CodecFormat.Json, Fruit, CodecFormat.Json].apply(streams)),
        port,
        (),
        (),
        "ws"
      )
        .flatMap { r =>
          sendAndReceiveLimited(r.toOption.get, 2, List(Fruit("apple"), Fruit("orange")))
        }
        .map(_ shouldBe List(Fruit("echo: apple"), Fruit("echo: orange")))
        .unsafeToFuture()
    }

    test("web sockets, client-terminated echo using fragmented frames") {
      send(
        endpoint.get
          .in("ws" / "echo" / "fragmented")
          .out(webSocketBody[String, CodecFormat.TextPlain, WebSocketFrame, CodecFormat.TextPlain].apply(streams)),
        port,
        (),
        (),
        "ws"
      )
        .flatMap { r =>
          sendAndReceiveLimited(r.toOption.get, 2, List("test"))
        }
        .map(_ shouldBe List(WebSocketFrame.Text("fragmented frame with echo: test", true, None)))
        .unsafeToFuture()
    }

    val errorOrWsEndpoint = endpoint.get
      .in(query[Boolean]("error"))
      .in("ws" / "error-or-echo")
      .errorOut(stringBody)
      .out(webSocketBody[String, CodecFormat.TextPlain, String, CodecFormat.TextPlain].apply(streams))

    test("web sockets, string client-terminated echo or error - success case") {
      send(errorOrWsEndpoint, port, (), false, "ws")
        .flatMap { r =>
          sendAndReceiveLimited(r.toOption.get, 2, List("test1", "test2"))
        }
        .map(_ shouldBe List("echo: test1", "echo: test2"))
        .unsafeToFuture()
    }

    test("web sockets, string client-terminated echo or error - error case") {
      send(errorOrWsEndpoint, port, (), true, "ws")
        .map(_ should matchPattern { case Left(_) => })
        .unsafeToFuture()
    }

    // TODO: tests for ping/pong (control frames handling)
  }
}
