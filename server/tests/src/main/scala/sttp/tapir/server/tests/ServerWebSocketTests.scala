package sttp.tapir.server.tests

import cats.effect.IO
import cats.syntax.all._
import io.circe.generic.auto._
import org.scalatest.matchers.should.Matchers._
import sttp.capabilities.{Streams, WebSockets}
import sttp.client3._
import sttp.monad.MonadError
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.server.interceptor.CustomiseInterceptors
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.tests.ServerMetricsTest._
import sttp.tapir.tests.Test
import sttp.tapir.tests.data.Fruit
import sttp.ws.{WebSocket, WebSocketFrame}

abstract class ServerWebSocketTests[F[_], S <: Streams[S], OPTIONS, ROUTE](
    createServerTest: CreateServerTest[F, S with WebSockets, OPTIONS, ROUTE],
    val streams: S
)(implicit
    m: MonadError[F]
) {
  import createServerTest._

  def functionToPipe[A, B](f: A => B): streams.Pipe[A, B]
  def emptyPipe[A, B]: streams.Pipe[A, B]

  private def stringWs = webSocketBody[String, CodecFormat.TextPlain, String, CodecFormat.TextPlain].apply(streams)
  private def stringEcho = functionToPipe((s: String) => s"echo: $s")

  def tests(): List[Test] = List(
    testServer(
      endpoint.out(stringWs),
      "string client-terminated echo"
    )((_: Unit) => pureResult(stringEcho.asRight[Unit])) { (backend, baseUri) =>
      basicRequest
        .response(asWebSocket { (ws: WebSocket[IO]) =>
          for {
            _ <- ws.sendText("test1")
            _ <- ws.sendText("test2")
            m1 <- ws.receiveText()
            m2 <- ws.receiveText()
            _ <- ws.close()
            m3 <- ws.eitherClose(ws.receiveText())
          } yield List(m1, m2, m3)
        })
        .get(baseUri.scheme("ws"))
        .send(backend)
        .map(_.body shouldBe Right(List("echo: test1", "echo: test2", Left(WebSocketFrame.Close(1000, "normal closure")))))
    }, {

      val reqCounter = newRequestCounter[F]
      val resCounter = newResponseCounter[F]
      val metrics = new MetricsRequestInterceptor[F](List(reqCounter, resCounter), Seq.empty)

      testServer(
        endpoint.out(stringWs).name("metrics"),
        interceptors = (ci: CustomiseInterceptors[F, OPTIONS]) => ci.metricsInterceptor(metrics)
      )((_: Unit) => pureResult(stringEcho.asRight[Unit])) { (backend, baseUri) =>
        basicRequest
          .response(asWebSocket { (ws: WebSocket[IO]) =>
            for {
              _ <- ws.sendText("test1")
              m <- ws.receiveText()
            } yield List(m)
          })
          .get(baseUri.scheme("ws"))
          .send(backend)
          .map { r =>
            r.body shouldBe Right(List("echo: test1"))
            reqCounter.metric.value.get() shouldBe 1
            resCounter.metric.value.get() shouldBe 1
          }
      }
    },
    testServer(endpoint.out(webSocketBody[Fruit, CodecFormat.Json, Fruit, CodecFormat.Json](streams)), "json client-terminated echo")(
      (_: Unit) => pureResult(functionToPipe((f: Fruit) => Fruit(s"echo: ${f.f}")).asRight[Unit])
    ) { (backend, baseUri) =>
      basicRequest
        .response(asWebSocket { (ws: WebSocket[IO]) =>
          for {
            _ <- ws.sendText("""{"f":"apple"}""")
            _ <- ws.sendText("""{"f":"orange"}""")
            m1 <- ws.receiveText()
            m2 <- ws.receiveText()
          } yield List(m1, m2)
        })
        .get(baseUri.scheme("ws"))
        .send(backend)
        .map(_.body shouldBe Right(List("""{"f":"echo: apple"}""", """{"f":"echo: orange"}""")))
    },
    testServer(
      endpoint.out(webSocketBody[String, CodecFormat.TextPlain, Option[String], CodecFormat.TextPlain](streams)),
      "string server-terminated echo"
    )((_: Unit) =>
      pureResult(functionToPipe[String, Option[String]] {
        case "end" => None
        case msg   => Some(s"echo: $msg")
      }.asRight[Unit])
    ) { (backend, baseUri) =>
      basicRequest
        .response(asWebSocket { (ws: WebSocket[IO]) =>
          for {
            _ <- ws.sendText("test1")
            _ <- ws.sendText("test2")
            _ <- ws.sendText("end")
            m1 <- ws.eitherClose(ws.receiveText())
            m2 <- ws.eitherClose(ws.receiveText())
            m3 <- ws.eitherClose(ws.receiveText())
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
      endpoint.out(webSocketBody[String, CodecFormat.TextPlain, String, CodecFormat.TextPlain](streams)),
      "failing pipe"
    )((_: Unit) =>
      pureResult(functionToPipe[String, String] {
        case "error-trigger" => throw new Exception("Boom!")
        case msg             => s"echo: $msg"
      }.asRight[Unit])
    ) { (backend, baseUri) =>
      basicRequest
        .response(asWebSocket { (ws: WebSocket[IO]) =>
          for {
            _ <- ws.sendText("test1")
            _ <- ws.sendText("test2")
            _ <- ws.sendText("error-trigger")
            m1 <- ws.eitherClose(ws.receiveText())
            m2 <- ws.eitherClose(ws.receiveText())
            m3 <- ws.eitherClose(ws.receiveText())
          } yield List(m1, m2, m3)
        })
        .get(baseUri.scheme("ws"))
        .send(backend)
        .map(
          _.body.map(_.map(_.left.map(_.statusCode))) shouldBe Right(
            List(Right("echo: test1"), Right("echo: test2"), Left(1011))
          )
        )
    },
    testServer(
      endpoint.out(webSocketBody[String, CodecFormat.TextPlain, String, CodecFormat.TextPlain](streams)),
      "empty client stream"
    )((_: Unit) => pureResult(emptyPipe.asRight[Unit])) { (backend, baseUri) =>
      basicRequest
        .response(asWebSocketAlways { (ws: WebSocket[IO]) => ws.eitherClose(ws.receiveText()) })
        .get(baseUri.scheme("ws"))
        .send(backend)
        .map(_.body.left.map(_.statusCode) shouldBe Left(WebSocketFrame.close.statusCode))
    },
    testServer(
      endpoint
        .in(isWebSocket)
        .errorOut(stringBody)
        .out(stringWs),
      "non web-socket request"
    )(isWS => if (isWS) pureResult(stringEcho.asRight) else pureResult("Not a WS!".asLeft)) { (backend, baseUri) =>
      basicRequest
        .response(asString)
        .get(baseUri.scheme("http"))
        .send(backend)
        .map(_.body shouldBe Left("Not a WS!"))
    }
  )

  // TODO: tests for ping/pong (control frames handling)
}
