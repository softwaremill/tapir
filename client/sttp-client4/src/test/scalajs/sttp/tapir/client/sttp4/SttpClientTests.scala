package sttp.tapir.client.sttp

import cats.effect.IO

import scala.concurrent.Future
import sttp.tapir.{DecodeResult, Endpoint}
import sttp.tapir.client.tests.ClientTests
import sttp.client4._
import sttp.tapir.client.sttp4.{SttpClientInterpreter, WebSocketToPipe}
import sttp.client4.fetch.FetchBackend
import sttp.tapir.client.sttp4.GenericRequestExtensions

abstract class SttpClientTests[R >: Any] extends ClientTests[R] {
  val backend = FetchBackend()
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
    val response: Future[Either[E, O]] = GenericRequestExtensions.sendRequest(backend, genReq).map(_.body)
    IO.fromFuture(IO(response))
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

    val response: Future[DecodeResult[Either[E, O]]] = GenericRequestExtensions.sendRequest(backend, genReq).map(_.body)
    IO.fromFuture(IO(response))
  }

  override protected def afterAll(): Unit = {
    backend.close()
    super.afterAll()
  }
}
