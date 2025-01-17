package sttp.tapir.client.sttp

import cats.effect.IO

import scala.concurrent.Future
import sttp.tapir.{DecodeResult, Endpoint}
import sttp.tapir.client.tests.ClientTests
import sttp.client4._

abstract class SttpClientTests[R >: Any] extends ClientTests[R] {
  val backend: SttpBackend[Future, R] = FetchBackend()
  def wsToPipe: WebSocketToPipe[R]

  override def send[A, I, E, O](
      e: Endpoint[A, I, E, O, R],
      port: Port,
      securityArgs: A,
      args: I,
      scheme: String = "http"
  ): IO[Either[E, O]] = {
    implicit val wst: WebSocketToPipe[R] = wsToPipe
    val response: Future[Either[E, O]] =
      SttpClientInterpreter()
        .toSecureRequestThrowDecodeFailures(e, Some(uri"$scheme://localhost:$port"))
        .apply(securityArgs)
        .apply(args)
        .send(backend)
        .map(_.body)
    IO.fromFuture(IO(response))
  }

  override def safeSend[A, I, E, O](
      e: Endpoint[A, I, E, O, R],
      port: Port,
      securityArgs: A,
      args: I
  ): IO[DecodeResult[Either[E, O]]] = {
    implicit val wst: WebSocketToPipe[R] = wsToPipe
    def response: Future[DecodeResult[Either[E, O]]] =
      SttpClientInterpreter()
        .toSecureRequest(e, Some(uri"http://localhost:$port"))
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
