package sttp.tapir.client.sttp4

import cats.effect.IO
import cats.effect.std.Dispatcher
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4._
import sttp.client4.httpclient.fs2.HttpClientFs2Backend
import sttp.tapir.client.sttp4.WebSocketToPipe
import sttp.tapir.client.tests.ClientTests
import sttp.tapir.{DecodeResult, Endpoint}

abstract class SttpClientTests[R >: WebSockets with Fs2Streams[IO]] extends ClientTests[R] {
  val (dispatcher, closeDispatcher) = Dispatcher.parallel[IO](false).allocated.unsafeRunSync()
  val backend: Backend[IO] = HttpClientFs2Backend[IO](dispatcher).unsafeRunSync()
  def wsToPipe: WebSocketToPipe[R]

  override def send[A, I, E, O](
      e: Endpoint[A, I, E, O, R],
      port: Port,
      securityArgs: A,
      args: I,
      scheme: String = "http"
  ): IO[Either[E, O]] = {
    implicit val wst: WebSocketToPipe[R] = wsToPipe
    val genReq = SttpClientInterpreter()
      .toSecureRequestThrowDecodeFailures(e, Some(uri"$scheme://localhost:$port"))
      .apply(securityArgs)
      .apply(args)

    GenericRequestExtensions
      .sendRequest(backend, genReq)
      .map(_.body)
  }

  override def safeSend[A, I, E, O](
      e: Endpoint[A, I, E, O, R],
      port: Port,
      securityArgs: A,
      args: I
  ): IO[DecodeResult[Either[E, O]]] = {
    implicit val wst: WebSocketToPipe[R] = wsToPipe
    val genReq = SttpClientInterpreter()
      .toSecureRequest(e, Some(uri"http://localhost:$port"))
      .apply(securityArgs)
      .apply(args)

    GenericRequestExtensions.sendRequest(backend, genReq).map(_.body)
  }

  override protected def afterAll(): Unit = {
    backend.close().unsafeRunSync()
    closeDispatcher.unsafeRunSync()
    super.afterAll()
  }
}
