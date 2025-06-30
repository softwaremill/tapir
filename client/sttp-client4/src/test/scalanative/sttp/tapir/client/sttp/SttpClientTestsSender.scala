package sttp.tapir.client.sttp4

import scala.util.Try

import sttp.tapir.{DecodeResult, Endpoint}
import sttp.tapir.client.tests.ClientTests
import sttp.client4._
import scala.concurrent.Future
import sttp.client4.curl.*

abstract class SttpClientTestsSender extends ClientTests[Any] {

  val backend: Backend[Try] = CurlTryBackend(verbose = false)

  override def send[A, I, E, O](
      e: Endpoint[A, I, E, O, Any],
      port: Port,
      securityArgs: A,
      args: I,
      scheme: String = "http"
  ): Future[Either[E, O]] = {
    val response: Try[Either[E, O]] =
      SttpClientInterpreter()
        .toSecureRequestThrowDecodeFailures[A, I, E, O](e, Some(uri"$scheme://localhost:$port"))
        .apply(securityArgs)
        .apply(args)
        .send(backend)
        .map(_.body)
    Future.fromTry(response)
  }

  override def safeSend[A, I, E, O](
      e: Endpoint[A, I, E, O, Any],
      port: Port,
      securityArgs: A,
      args: I
  ): Future[DecodeResult[Either[E, O]]] = {
    def response: Try[DecodeResult[Either[E, O]]] =
      SttpClientInterpreter()
        .toSecureRequest[A, I, E, O](e, Some(uri"http://localhost:$port"))
        .apply(securityArgs)
        .apply(args)
        .send(backend)
        .map(_.body)
    Future.fromTry(response)
  }

  override protected def afterAll(): Unit = {
    backend.close()
    super.afterAll()
  }
}
