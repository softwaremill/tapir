package sttp.tapir.client.sttp

import cats.effect.{Blocker, ContextShift, IO}
import sttp.capabilities.fs2.Fs2Streams
import sttp.capabilities.{Effect, WebSockets}
import sttp.client3._
import sttp.client3.asynchttpclient.fs2.AsyncHttpClientFs2Backend
import sttp.tapir.client.tests.ClientTests
import sttp.tapir.{DecodeResult, Endpoint}

import scala.concurrent.ExecutionContext

abstract class SttpClientTests[R >: WebSockets with Fs2Streams[IO]] extends ClientTests[R, IO] {
  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.Implicits.global)
  val backend: SttpBackend[IO, R] =
    AsyncHttpClientFs2Backend[IO](Blocker.liftExecutionContext(ExecutionContext.Implicits.global)).unsafeRunSync()
  def wsToPipe: WebSocketToPipe[R]

  override def send[I, E, O](
      e: Endpoint[I, E, O, R with Effect[IO]],
      port: Port,
      args: I,
      scheme: String = "http"
  ): IO[Either[E, O]] = {
    implicit val wst: WebSocketToPipe[R] = wsToPipe
    SttpClientInterpreter.toClientThrowDecodeFailures(e, Some(uri"$scheme://localhost:$port"), backend).apply(args)
  }

  override def safeSend[I, E, O](
      e: Endpoint[I, E, O, R with Effect[IO]],
      port: Port,
      args: I
  ): IO[DecodeResult[Either[E, O]]] = {
    implicit val wst: WebSocketToPipe[R] = wsToPipe
    SttpClientInterpreter.toClient(e, Some(uri"http://localhost:$port"), backend).apply(args)
  }

  override protected def afterAll(): Unit = {
    backend.close().unsafeRunSync()
    super.afterAll()
  }
}
