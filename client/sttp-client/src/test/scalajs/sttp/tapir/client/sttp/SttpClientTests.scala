package sttp.tapir.client.sttp

import cats.effect.{ContextShift, IO}
import sttp.capabilities.Effect

import scala.concurrent.Future
import sttp.tapir.{DecodeResult, Endpoint}
import sttp.tapir.client.tests.ClientTests
import sttp.client3._

abstract class SttpClientTests[R >: Any] extends ClientTests[R, Future] {
  implicit val cs: ContextShift[IO] = IO.contextShift(executionContext)
  val backend: SttpBackend[Future, R] = FetchBackend()
  def wsToPipe: WebSocketToPipe[R]

  override def send[I, E, O](e: Endpoint[I, E, O, R with Effect[Future]], port: Port, args: I, scheme: String = "http"): IO[Either[E, O]] = {
    implicit val wst: WebSocketToPipe[R] = wsToPipe
    val response: Future[Either[E, O]] =
      SttpClientInterpreter.toClientThrowDecodeFailures(e, Some(uri"$scheme://localhost:$port"), backend).apply(args)
    IO.fromFuture(IO(response))
  }

  override def safeSend[I, E, O](
      e: Endpoint[I, E, O, R with Effect[Future]],
      port: Port,
      args: I
  ): IO[DecodeResult[Either[E, O]]] = {
    implicit val wst: WebSocketToPipe[R] = wsToPipe
    def response: Future[DecodeResult[Either[E, O]]] =
      SttpClientInterpreter.toClient(e, Some(uri"http://localhost:$port"), backend).apply(args)
    IO.fromFuture(IO(response))
  }

  override protected def afterAll(): Unit = {
    backend.close()
    super.afterAll()
  }
}
