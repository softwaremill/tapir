package sttp.tapir.client.sttp4

import sttp.tapir.{DecodeResult, Endpoint}
import sttp.tapir.client.tests.ClientTests
import sttp.client4._
import sttp.client4.fetch.FetchBackend
import concurrent.Future

abstract class SttpClientTestsSender extends ClientTests[Any] {
  val backend = FetchBackend()

  override def send[A, I, E, O](
      e: Endpoint[A, I, E, O, Any],
      port: Port,
      securityArgs: A,
      args: I,
      scheme: String = "http"
  ): Future[Either[E, O]] = {
    SttpClientInterpreter()
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
  ): Future[DecodeResult[Either[E, O]]] = {
    SttpClientInterpreter()
      .toSecureRequest[A, I, E, O](e, Some(uri"http://localhost:$port"))
      .apply(securityArgs)
      .apply(args)
      .send(backend)
      .map(_.body)
  }

  override protected def afterAll(): Unit = {
    backend.close()
    super.afterAll()
  }
}
