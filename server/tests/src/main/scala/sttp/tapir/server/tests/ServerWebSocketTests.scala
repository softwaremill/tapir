package sttp.tapir.server.tests

import cats.effect.IO
import cats.syntax.all._
import io.circe.generic.auto._
import org.scalatest.matchers.should.Matchers._
import org.scalatest.EitherValues
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

import scala.concurrent.duration._
import sttp.tapir.model.UnsupportedWebSocketFrameException

abstract class ServerWebSocketTests[F[_], S <: Streams[S], OPTIONS, ROUTE](
    createServerTest: CreateServerTest[F, S with WebSockets, OPTIONS, ROUTE],
    val streams: S,
    autoPing: Boolean,
    failingPipe: Boolean,
    handlePong: Boolean
)(implicit
    m: MonadError[F]
) extends EitherValues {
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
      endpoint.out(
        webSocketBody[String, CodecFormat.TextPlain, String, CodecFormat.TextPlain](streams)
          .autoPing(None)
          .autoPongOnPing(true)
      ),
      "pong on ping"
    )((_: Unit) => pureResult(stringEcho.asRight[Unit])) { (backend, baseUri) =>
      basicRequest
        .response(asWebSocket { (ws: WebSocket[IO]) =>
          for {
            _ <- ws.sendText("test1")
            _ <- ws.send(WebSocketFrame.Ping("test-ping-text".getBytes()))
            m1 <- ws.receive()
            _ <- ws.sendText("test2")
            m2 <- ws.receive()
          } yield List(m1, m2)
        })
        .get(baseUri.scheme("ws"))
        .send(backend)
        .map((r: Response[Either[String, List[WebSocketFrame]]]) =>
          assert(
            r.body.value exists {
              case WebSocketFrame.Pong(array) => array sameElements "test-ping-text".getBytes
              case _                          => false
            },
            s"Missing Pong(test-ping-text) in ${r.body}"
          )
        )
    },
    testServer(
      endpoint.out(
        webSocketBody[String, CodecFormat.TextPlain, String, CodecFormat.TextPlain](streams)
          .autoPing(None)
          .autoPongOnPing(false)
      ),
      "not pong on ping if disabled"
    )((_: Unit) => pureResult(stringEcho.asRight[Unit])) { (backend, baseUri) =>
      basicRequest
        .response(asWebSocket { (ws: WebSocket[IO]) =>
          for {
            _ <- ws.sendText("test1")
            _ <- ws.send(WebSocketFrame.Ping("test-ping-text".getBytes()))
            m1 <- ws.receiveText()
            _ <- ws.sendText("test2")
            m2 <- ws.receiveText()
          } yield List(m1, m2)
        })
        .get(baseUri.scheme("ws"))
        .send(backend)
        .map(
          _.body shouldBe Right(List("echo: test1", "echo: test2"))
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
  ) ++ autoPingTests ++ failingPipeTests ++ handlePongTests

  val autoPingTests =
    if (autoPing)
      List(
        testServer(
          endpoint.out(
            webSocketBody[String, CodecFormat.TextPlain, String, CodecFormat.TextPlain](streams)
              .autoPing(Some((50.millis, WebSocketFrame.ping)))
          ),
          "auto ping"
        )((_: Unit) => pureResult(stringEcho.asRight[Unit])) { (backend, baseUri) =>
          basicRequest
            .response(asWebSocket { (ws: WebSocket[IO]) =>
              for {
                _ <- ws.sendText("test1")
                _ <- IO.sleep(150.millis)
                _ <- ws.sendText("test2")
                m1 <- ws.receive()
                m2 <- ws.receive()
                _ <- ws.sendText("test3")
                m3 <- ws.receive()
              } yield List(m1, m2, m3)
            })
            .get(baseUri.scheme("ws"))
            .send(backend)
            .map((r: Response[Either[String, List[WebSocketFrame]]]) =>
              assert(r.body.value.exists(_.isInstanceOf[WebSocketFrame.Ping]), s"Missing Ping frame in WS responses: $r")
            )
        }
      )
    else List.empty

  // Optional, because some backends don't handle exceptions in the pipe gracefully, they just swallow it silently and hang forever
  val failingPipeTests =
    if (failingPipe)
      List(
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
            .map { r =>
              val results = r.body.map(_.map(_.left.map(_.statusCode))).value
              results.take(2) shouldBe
                List(Right("echo: test1"), Right("echo: test2"))
              val closeCode = results.last.left.value
              assert(closeCode == 1000 || closeCode == 1011) // some servers respond with Close(normal), some with Close(error)
            }
        }
      )
    else List.empty

  val handlePongTests =
    if (handlePong)
      List(
        testServer(
          {
            implicit def textOrPongWebSocketFrame[A, CF <: CodecFormat](implicit
                stringCodec: Codec[String, A, CF]
            ): Codec[WebSocketFrame, A, CF] =
              Codec // A custom codec to handle Pongs
                .id[WebSocketFrame, CF](stringCodec.format, Schema.string)
                .mapDecode {
                  case WebSocketFrame.Text(p, _, _) => stringCodec.decode(p)
                  case WebSocketFrame.Pong(payload) =>
                    stringCodec.decode(new String(payload))
                  case f => DecodeResult.Error(f.toString, new UnsupportedWebSocketFrameException(f))
                }(a => WebSocketFrame.text(stringCodec.encode(a)))
                .schema(stringCodec.schema)

            endpoint.out(
              webSocketBody[String, CodecFormat.TextPlain, String, CodecFormat.TextPlain](streams)
                .autoPing(None)
                .ignorePong(false)
            )
          },
          "not ignore pong"
        )((_: Unit) => pureResult(stringEcho.asRight[Unit])) { (backend, baseUri) =>
          basicRequest
            .response(asWebSocket { (ws: WebSocket[IO]) =>
              for {
                _ <- ws.sendText("test1")
                _ <- ws.send(WebSocketFrame.Pong("test-pong-text".getBytes()))
                m1 <- ws.receiveText()
                _ <- ws.sendText("test2")
                m2 <- ws.receiveText()
              } yield List(m1, m2)
            })
            .get(baseUri.scheme("ws"))
            .send(backend)
            .map(_.body shouldBe Right(List("echo: test1", "echo: test-pong-text")))
        }
      )
    else List.empty
}
