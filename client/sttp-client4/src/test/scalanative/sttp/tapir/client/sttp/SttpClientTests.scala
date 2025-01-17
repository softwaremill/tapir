package sttp.tapir.client.sttp

import scala.util.Try
import cats.effect.IO

import sttp.tapir.{DecodeResult, Endpoint}
import sttp.tapir.client.tests.ClientTests
import sttp.client4._

abstract class SttpClientTests[R >: Any] extends ClientTests[R] {

  val backend: SttpBackend[Try, R] = CurlTryBackend(verbose = false)
  def wsToPipe: WebSocketToPipe[R]

  override def send[A, I, E, O](
      e: Endpoint[A, I, E, O, R],
      port: Port,
      securityArgs: A,
      args: I,
      scheme: String = "http"
  ): IO[Either[E, O]] = {
    implicit val wst: WebSocketToPipe[R] = wsToPipe
    val response: Try[Either[E, O]] =
      SttpClientInterpreter()
        .toSecureRequestThrowDecodeFailures(e, Some(uri"$scheme://localhost:$port"))
        .apply(securityArgs)
        .apply(args)
        .send(backend)
        .map(_.body)
    IO.fromTry(response)
  }

  override def safeSend[A, I, E, O](
      e: Endpoint[A, I, E, O, R],
      port: Port,
      securityArgs: A,
      args: I
  ): IO[DecodeResult[Either[E, O]]] = {
    implicit val wst: WebSocketToPipe[R] = wsToPipe
    def response: Try[DecodeResult[Either[E, O]]] =
      SttpClientInterpreter()
        .toSecureRequest(e, Some(uri"http://localhost:$port"))
        .apply(securityArgs)
        .apply(args)
        .send(backend)
        .map(_.body)
    IO.fromTry(response)
  }

  override protected def afterAll(): Unit = {
    backend.close()
    super.afterAll()
  }
}
