package sttp.tapir.client.sttp

import cats.effect.IO
import sttp.capabilities.WebSockets
import sttp.capabilities.zio.ZioStreams
import sttp.client3._
import sttp.client3.httpclient.zio.HttpClientZioBackend
import sttp.tapir.client.tests.ClientTests
import sttp.tapir.{DecodeResult, Endpoint}
import zio.Task

abstract class SttpClientZioTests[R >: WebSockets with ZioStreams] extends ClientTests[R] {
  val backend: SttpBackend[Task, R] = zio.Runtime.default.unsafeRun(HttpClientZioBackend())
  def wsToPipe: WebSocketToPipe[R]

  override def send[A, I, E, O](
      e: Endpoint[A, I, E, O, R],
      port: Port,
      securityArgs: A,
      args: I,
      scheme: String = "http"
  ): IO[Either[E, O]] =
    IO.fromFuture(IO.delay {
      implicit val wst: WebSocketToPipe[R] = wsToPipe
      val send = SttpClientInterpreter()
        .toSecureRequestThrowDecodeFailures(e, Some(uri"$scheme://localhost:$port"))
        .apply(securityArgs)
        .apply(args)
        .send(backend)
        .map(_.body)
      zio.Runtime.default.unsafeRunToFuture(send).future
    })

  override def safeSend[A, I, E, O](
      e: Endpoint[A, I, E, O, R],
      port: Port,
      securityArgs: A,
      args: I
  ): IO[DecodeResult[Either[E, O]]] =
    IO.fromFuture(IO.delay {
      implicit val wst: WebSocketToPipe[R] = wsToPipe
      val send = SttpClientInterpreter()
        .toSecureRequest(e, Some(uri"http://localhost:$port"))
        .apply(securityArgs)
        .apply(args)
        .send(backend)
        .map(_.body)
      zio.Runtime.default.unsafeRunToFuture(send).future
    })

  override protected def afterAll(): Unit = {
    zio.Runtime.default.unsafeRun(backend.close())
    super.afterAll()
  }
}
