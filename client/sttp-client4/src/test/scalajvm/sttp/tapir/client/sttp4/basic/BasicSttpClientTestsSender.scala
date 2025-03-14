package sttp.tapir.client.sttp4.basic

import cats.effect.IO
import cats.effect.std.Dispatcher
import sttp.client4._
import sttp.client4.httpclient.fs2.HttpClientFs2Backend
import sttp.tapir.client.sttp4.basic.BasicSttpClientInterpreter
import sttp.tapir.client.tests.ClientTests
import sttp.tapir.{DecodeResult, Endpoint}

abstract class BasicSttpClientTestsSender extends ClientTests[Any] {
  val (dispatcher, closeDispatcher) = Dispatcher.parallel[IO](false).allocated.unsafeRunSync()
  val backend: Backend[IO] = HttpClientFs2Backend[IO](dispatcher).unsafeRunSync()

  override def send[A, I, E, O](
      e: Endpoint[A, I, E, O, Any],
      port: Port,
      securityArgs: A,
      args: I,
      scheme: String = "http"
  ): IO[Either[E, O]] = {
    BasicSttpClientInterpreter()
      .toSecureRequestThrowDecodeFailures[A, I, E, O](e, Some(uri"$scheme://localhost:$port"))
      .apply(securityArgs)
      .apply(args)
      .send(backend)
      .map(_.body)
  }

  override def safeSend[A, I, E, O](
      e: Endpoint[A, I, E, O, Any],
      port: Port,
      securityArgs: A,
      args: I
  ): IO[DecodeResult[Either[E, O]]] = {
    BasicSttpClientInterpreter()
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
