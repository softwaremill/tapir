package sttp.tapir.client.sttp4

import cats.effect.IO
import cats.effect.std.Dispatcher
import sttp.client4._
import sttp.client4.httpclient.fs2.HttpClientFs2Backend
import sttp.tapir.client.sttp4.SttpClientInterpreter
import sttp.tapir.client.tests.ClientTestsIO
import sttp.tapir.{DecodeResult, Endpoint}

abstract class SttpClientTestsSender extends ClientTestsIO[Any] {
  val (dispatcher, closeDispatcher) = Dispatcher.parallel[IO](false).allocated.unsafeRunSync()
  val backend: Backend[IO] = HttpClientFs2Backend[IO](dispatcher).unsafeRunSync()

  override def sendIO[A, I, E, O](
      e: Endpoint[A, I, E, O, Any],
      port: Port,
      securityArgs: A,
      args: I,
      scheme: String = "http"
  ): IO[Either[E, O]] = {
    SttpClientInterpreter()
      .toSecureRequestThrowDecodeFailures[A, I, E, O](e, Some(uri"$scheme://localhost:$port"))
      .apply(securityArgs)
      .apply(args)
      .send(backend)
      .map(_.body)
  }

  override def safeSendIO[A, I, E, O](
      e: Endpoint[A, I, E, O, Any],
      port: Port,
      securityArgs: A,
      args: I
  ): IO[DecodeResult[Either[E, O]]] = {
    SttpClientInterpreter()
      .toSecureRequest[A, I, E, O](e, Some(uri"http://localhost:$port"))
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
