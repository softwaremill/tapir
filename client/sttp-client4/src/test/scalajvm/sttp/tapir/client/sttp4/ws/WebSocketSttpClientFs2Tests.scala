package sttp.tapir.client.sttp4.ws

import _root_.fs2._
import cats.effect.IO
import cats.effect.unsafe._
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir._
import sttp.tapir.client.sttp4.WebSocketToPipe
import sttp.tapir.client.sttp4.ws.fs2._
import sttp.tapir.client.tests.ClientWebSocketTests
import sttp.ws.WebSocketFrame

import scala.concurrent.Future
import scala.concurrent.duration._

class WebSocketSttpClientFs2Tests extends WebSocketSttpClientFs2TestsSender with ClientWebSocketTests[Fs2Streams[IO]] {
  private implicit val ioRT: IORuntime = cats.effect.unsafe.implicits.global

  override val streams: Fs2Streams[IO] = Fs2Streams[IO]
  override def wsToPipe: WebSocketToPipe[WebSockets with Fs2Streams[IO]] = implicitly

  override def sendAndReceiveLimited[A, B](p: Pipe[IO, A, B], receiveCount: Port, as: List[A]): Future[List[B]] = {
    Stream(as: _*).through(p).take(receiveCount.longValue).compile.toList.unsafeToFuture()
  }

  webSocketTests()

  test("web sockets, terminate the resulting stream when the server sends a Close frame, even if the input stream is still emitting") {
    val serverClosingEndpoint = endpoint.get
      .in("ws" / "echo" / "fragmented")
      .out(webSocketBody[String, CodecFormat.TextPlain, WebSocketFrame, CodecFormat.TextPlain].apply(streams))

    send(serverClosingEndpoint, port, (), (), "ws").flatMap { r =>
      val pipe = r.toOption.get
      // The input stream emits one item, then never emits again. Without the fix,
      // the resulting stream would not terminate after the server's Close frame,
      // because `sends` would still be waiting on the input.
      val firstThenIndefinite: Stream[IO, String] = Stream("test") ++ Stream.never[IO]

      pipe(firstThenIndefinite).compile.toList
        .timeout(5.seconds)
        .map(_ shouldBe List(WebSocketFrame.Text("fragmented frame with echo: test", true, None)))
        .unsafeToFuture()
    }
  }
}
