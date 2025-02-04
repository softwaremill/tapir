package sttp.tapir.client.sttp4

import cats.effect.IO
import fs2._
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4._
import sttp.client4.httpclient.fs2.HttpClientFs2Backend
import sttp.tapir._
import sttp.tapir.client.tests.ClientWebSocketTests
import sttp.tapir.client.sttp4.ws.fs2._
import sttp.tapir.client.sttp4.WebSocketToPipe

class SttpClientWebSocketTests extends SttpClientTests[WebSockets with Fs2Streams[IO]] with ClientWebSocketTests[Fs2Streams[IO]] {
  override val streams: Fs2Streams[IO] = Fs2Streams[IO]
  override def wsToPipe: WebSocketToPipe[WebSockets with Fs2Streams[IO]] = implicitly

  override def sendAndReceiveLimited[A, B](p: Pipe[IO, A, B], receiveCount: Port, as: List[A]): IO[List[B]] = {
    Stream(as: _*).through(p).take(receiveCount.longValue).compile.toList
  }

  webSocketTests()

  test("web sockets, string echo, custom header") {
    // HttpClient doesn't expose web socket response headers
    HttpClientFs2Backend
      .resource[IO]()
      .use { httpClientBackend =>
        def sendAsResponse[A, I, E, O](
            e: Endpoint[A, I, E, O, WebSockets with Fs2Streams[IO]],
            port: Port,
            securityArgs: A,
            args: I,
            scheme: String
        ): IO[Response[Either[E, O]]] = {
          val genReq = SttpClientInterpreter()
            .toSecureRequestThrowDecodeFailures(e, Some(uri"$scheme://localhost:$port"))
            .apply(securityArgs)
            .apply(args)

          GenericRequestExtensions.sendRequest(httpClientBackend, genReq)
        }

        sendAsResponse(
          endpoint.get
            .in("ws" / "echo" / "header")
            .out(webSocketBody[String, CodecFormat.TextPlain, String, CodecFormat.TextPlain].apply(streams)),
          port,
          (),
          (),
          "ws"
        )
          .flatMap { r =>
            r.header("Correlation-id") shouldBe Some("ABC-DEF-123")
            sendAndReceiveLimited(r.body.toOption.get, 2, List("test1", "test2"))
          }
          .map(_ shouldBe List("echo: test1", "echo: test2"))
      }
      .unsafeToFuture()
  }
}
