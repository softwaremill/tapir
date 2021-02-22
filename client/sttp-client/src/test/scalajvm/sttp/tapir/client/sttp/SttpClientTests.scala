package sttp.tapir.client.sttp

import cats.effect.{Blocker, ContextShift, IO}
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3._
import sttp.client3.httpclient.fs2.HttpClientFs2Backend
import sttp.tapir.client.tests.ClientTests
import sttp.tapir.{DecodeResult, Endpoint}

import scala.concurrent.ExecutionContext

abstract class SttpClientTests[R >: WebSockets with Fs2Streams[IO]] extends ClientTests[R] {
  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.Implicits.global)
  val backend: SttpBackend[IO, R] =
    HttpClientFs2Backend[IO](Blocker.liftExecutionContext(ExecutionContext.Implicits.global)).unsafeRunSync()
  def wsToPipe: WebSocketToPipe[R]

  override def send[I, E, O, FN[_]](e: Endpoint[I, E, O, R], port: Port, args: I, scheme: String = "http"): IO[Either[E, O]] = {
    implicit val wst: WebSocketToPipe[R] = wsToPipe
    SttpClientInterpreter.toRequestThrowDecodeFailures(e, Some(uri"$scheme://localhost:$port")).apply(args).send(backend).map(_.body)
  }

  override def safeSend[I, E, O, FN[_]](
      e: Endpoint[I, E, O, R],
      port: Port,
      args: I
  ): IO[DecodeResult[Either[E, O]]] = {
    implicit val wst: WebSocketToPipe[R] = wsToPipe
    SttpClientInterpreter.toRequest(e, Some(uri"http://localhost:$port")).apply(args).send(backend).map(_.body)
  }

  override protected def afterAll(): Unit = {
    backend.close().unsafeRunSync()
    super.afterAll()
  }
}
