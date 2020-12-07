package sttp.tapir.client.sttp

import cats.effect.{ContextShift, IO}

import scala.concurrent.Future
import sttp.tapir.{DecodeResult, Endpoint}
import sttp.tapir.client.tests.ClientTests
import sttp.client3._

abstract class SttpClientTests[R >: Any] extends ClientTests[R] {
  implicit val cs: ContextShift[IO] = IO.contextShift(executionContext)
  val backend: SttpBackend[Future, R] = FetchBackend()
  def wsToPipe: WebSocketToPipe[R]

  override def send[I, E, O, FN[_]](e: Endpoint[I, E, O, R], port: Port, args: I, scheme: String = "http"): IO[Either[E, O]] = {
    implicit val wst: WebSocketToPipe[R] = wsToPipe
    val response: Future[Either[E, O]] =
      e.toSttpRequestUnsafe(uri"$scheme://localhost:$port").apply(args).send(backend).map(_.body)
    IO.fromFuture(IO(response))
  }

  override def safeSend[I, E, O, FN[_]](
      e: Endpoint[I, E, O, R],
      port: Port,
      args: I
  ): IO[DecodeResult[Either[E, O]]] = {
    implicit val wst: WebSocketToPipe[R] = wsToPipe
    def response: Future[DecodeResult[Either[E, O]]] =
      e.toSttpRequest(uri"http://localhost:$port").apply(args).send(backend).map(_.body)
    IO.fromFuture(IO(response))
  }

  override protected def afterAll(): Unit = {
    backend.close()
    super.afterAll()
  }
}
