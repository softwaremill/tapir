package sttp.tapir.client.sttp4.basic

import cats.effect.IO
import sttp.tapir.{DecodeResult, Endpoint}
import sttp.tapir.client.tests.ClientTests
import sttp.client4._
import sttp.client4.fetch.FetchBackend

abstract class BasicSttpClientTestsSender extends ClientTests[Any] {
  val backend = FetchBackend()

  override def send[A, I, E, O](
      e: Endpoint[A, I, E, O, Any],
      port: Port,
      securityArgs: A,
      args: I,
      scheme: String = "http"
  ): IO[Either[E, O]] = {
    val response = BasicSttpClientInterpreter()
      .toSecureRequestThrowDecodeFailures[A, I, E, O](e, Some(uri"$scheme://localhost:$port"))
      .apply(securityArgs)
      .apply(args)
      .send(backend)
      .map(_.body)
    IO.fromFuture(IO(response))
  }

  override def safeSend[A, I, E, O](
      e: Endpoint[A, I, E, O, Any],
      port: Port,
      securityArgs: A,
      args: I
  ): IO[DecodeResult[Either[E, O]]] = {
    val response = BasicSttpClientInterpreter()
      .toSecureRequest[A, I, E, O](e, Some(uri"http://localhost:$port"))
      .apply(securityArgs)
      .apply(args)
      .send(backend)
      .map(_.body)

    IO.fromFuture(IO(response))
  }

  override protected def afterAll(): Unit = {
    backend.close()
    super.afterAll()
  }
}
