package sttp.tapir.client.sttp4.stream

import cats.effect.IO
import cats.effect.std.Dispatcher
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4._
import sttp.client4.httpclient.fs2.HttpClientFs2Backend
import sttp.tapir.client.tests.ClientTests
import sttp.tapir.{DecodeResult, Endpoint}

abstract class StreamSttpClientTestsSender extends ClientTests[Fs2Streams[IO]] {
  val (dispatcher, closeDispatcher) = Dispatcher.parallel[IO](false).allocated.unsafeRunSync()
  val backend: StreamBackend[IO, Fs2Streams[IO]] = HttpClientFs2Backend[IO](dispatcher).unsafeRunSync()

  override def send[A, I, E, O](
      e: Endpoint[A, I, E, O, Fs2Streams[IO]],
      port: Port,
      securityArgs: A,
      args: I,
      scheme: String = "http"
  ): IO[Either[E, O]] = {
    StreamSttpClientInterpreter()
      .toSecureRequestThrowDecodeFailures[A, I, E, O, Fs2Streams[IO]](e, Some(uri"$scheme://localhost:$port"))
      .apply(securityArgs)
      .apply(args)
      .send(backend)
      .map(_.body)
  }

  override def safeSend[A, I, E, O](
      e: Endpoint[A, I, E, O, Fs2Streams[IO]],
      port: Port,
      securityArgs: A,
      args: I
  ): IO[DecodeResult[Either[E, O]]] = {
    StreamSttpClientInterpreter()
      .toSecureRequest[A, I, E, O, Fs2Streams[IO]](e, Some(uri"http://localhost:$port"))
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
