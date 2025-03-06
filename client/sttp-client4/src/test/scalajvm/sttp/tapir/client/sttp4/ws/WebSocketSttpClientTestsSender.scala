package sttp.tapir.client.sttp4.ws

import cats.effect.IO
import cats.effect.std.Dispatcher
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4._
import sttp.client4.httpclient.fs2.HttpClientFs2Backend
import sttp.tapir.client.sttp4.WebSocketToPipe
import sttp.tapir.client.tests.ClientTests
import sttp.tapir.{DecodeResult, Endpoint}

abstract class WebSocketSttpClientTestsSender extends ClientTests[WebSockets with Fs2Streams[IO]] {
  val (dispatcher, closeDispatcher) = Dispatcher.parallel[IO](false).allocated.unsafeRunSync()
  val backend: WebSocketBackend[IO] = HttpClientFs2Backend[IO](dispatcher).unsafeRunSync()
  def wsToPipe: WebSocketToPipe[WebSockets with Fs2Streams[IO]]

  override def send[A, I, E, O](
      e: Endpoint[A, I, E, O, WebSockets with Fs2Streams[IO]],
      port: Port,
      securityArgs: A,
      args: I,
      scheme: String = "http"
  ): IO[Either[E, O]] = {
    implicit val wst: WebSocketToPipe[WebSockets with Fs2Streams[IO]] = wsToPipe
    WebSocketSttpClientInterpreter()
      .toSecureRequestThrowDecodeFailures[IO, A, I, E, O, WebSockets with Fs2Streams[IO]](e, Some(uri"$scheme://localhost:$port"))
      .apply(securityArgs)
      .apply(args)
      .send(backend)
      .map(_.body)
  }

  override def safeSend[A, I, E, O](
      e: Endpoint[A, I, E, O, WebSockets with Fs2Streams[IO]],
      port: Port,
      securityArgs: A,
      args: I
  ): IO[DecodeResult[Either[E, O]]] = {
    implicit val wst: WebSocketToPipe[WebSockets with Fs2Streams[IO]] = wsToPipe
    WebSocketSttpClientInterpreter()
      .toSecureRequest(e, Some(uri"http://localhost:$port"))
      .apply(securityArgs)
      .apply(args)
      .send(backend)
      .map(_.body)
  }

  override protected def afterAll(): Unit = {
    backend.close().unsafeRunSync()
    closeDispatcher.unsafeRunSync()
    super.afterAll()
  }
}
