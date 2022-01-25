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
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.tests.ServerMetricsTest._
import sttp.tapir.tests.Test
import sttp.tapir.tests.data.Fruit
import sttp.ws.{WebSocket, WebSocketFrame}

abstract class ServerWebSocketTests[F[_], S <: Streams[S], ROUTE](
    createServerTest: CreateServerTest[F, S with WebSockets, ROUTE],
    val streams: S,
    val concatenateFragmentedFrames: Boolean = true
)(implicit
    m: MonadError[F]
) {
  import createServerTest._

  def functionToPipe[A, B](f: A => B): streams.Pipe[A, B]

  private def stringWs = webSocketBody[String, CodecFormat.TextPlain, String, CodecFormat.TextPlain].apply(streams)
  private def stringEcho = functionToPipe((s: String) => s"echo: $s")

  def tests(): List[Test] = {
    val basicTests = List(
      testServer(
        endpoint.out(stringWs),
        "string client-terminated echo"
      )((_: Unit) => pureResult(stringEcho.asRight[Unit])) { (backend, baseUri) =>
        basicRequest
          .response(asWebSocket { (ws: WebSocket[IO]) =>
            for {
              _ <- ws.sendText("test1")
              m1 <- ws.receiveText()
              _ <- ws.sendText("test2")
              m2 <- ws.receiveText()
            } yield List(m1, m2)
          })
          .get(baseUri.scheme("ws"))
          .send(backend)
          .map(_.body shouldBe Right(List("echo: test1", "echo: test2")))
      }, {

        val reqCounter = newRequestCounter[F]
        val resCounter = newResponseCounter[F]
        val metrics = new MetricsRequestInterceptor[F](List(reqCounter, resCounter), Seq.empty)

        testServer(endpoint.out(stringWs).name("metrics"), metricsInterceptor = metrics.some)((_: Unit) =>
          pureResult(stringEcho.asRight[Unit])
        ) { (backend, baseUri) =>
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
              m1 <- ws.receiveText()
              _ <- ws.sendText("""{"f":"orange"}""")
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
              m1 <- ws.eitherClose(ws.receiveText())
              _ <- ws.sendText("test2")
              m2 <- ws.eitherClose(ws.receiveText())
              _ <- ws.sendText("end")
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
      },
      testServer(
        endpoint.out(stringWs),
        "pong on ping"
      )((_: Unit) => pureResult(stringEcho.asRight[Unit])) { (backend, baseUri) =>
        basicRequest
          .response(asWebSocket { (ws: WebSocket[IO]) =>
            for {
              _ <- ws.send(WebSocketFrame.ping)
              pong <- ws.receive()
            } yield pong
          })
          .get(baseUri.scheme("ws"))
          .send(backend)
          .map(_.body.map(_.isInstanceOf[WebSocketFrame.Pong]) shouldBe Right(true))
      }
    )

    val concatenateFramesTest = List(
      testServer(
        endpoint.out(stringWs.concatenateFragmentedFrames(true)),
        "concatenate fragmented frames"
      )((_: Unit) => pureResult(stringEcho.asRight[Unit])) { (backend, baseUri) =>
        basicRequest
          .response(asWebSocket { (ws: WebSocket[IO]) =>
            for {
              _ <- ws.send(WebSocketFrame.Text("hello-", finalFragment = false, rsv = None))
              _ <- ws.send(WebSocketFrame.Text("from-", finalFragment = false, rsv = None))
              _ <- ws.send(WebSocketFrame.Text("server", finalFragment = true, rsv = None))
              text <- ws.receiveText()
            } yield text
          })
          .get(baseUri.scheme("ws"))
          .send(backend)
          .map(_.body shouldBe Right("echo: hello-from-server"))
      }
    )

    basicTests ++ (if (concatenateFragmentedFrames) concatenateFramesTest else Nil)
  }
}
