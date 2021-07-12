package sttp.tapir.client.sttp

import cats.effect.{IO, Resource}
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3._
import sttp.client3.httpclient.fs2.HttpClientFs2Backend
import sttp.tapir.client.tests.ClientTests
import sttp.tapir.{DecodeResult, Endpoint}

abstract class SttpClientTests[R >: WebSockets with Fs2Streams[IO]] extends ClientTests[R] {

  val backend: Resource[IO, SttpBackend[IO, Fs2Streams[IO] with WebSockets]] = HttpClientFs2Backend.resource[IO]()
  def wsToPipe: WebSocketToPipe[R]

  override def send[I, E, O](e: Endpoint[I, E, O, R], port: Port, args: I, scheme: String = "http"): IO[Either[E, O]] = {
    backend.use { b =>
      implicit val wst: WebSocketToPipe[R] = wsToPipe
      SttpClientInterpreter().toRequestThrowDecodeFailures(e, Some(uri"$scheme://localhost:$port")).apply(args).send(b).map(_.body)
    }
  }

  override def safeSend[I, E, O](
      e: Endpoint[I, E, O, R],
      port: Port,
      args: I
  ): IO[DecodeResult[Either[E, O]]] = {
    backend.use { b =>
      implicit val wst: WebSocketToPipe[R] = wsToPipe
      SttpClientInterpreter.toRequest(e, Some(uri"http://localhost:$port")).apply(args).send(b).map(_.body)
    }
  }
}
