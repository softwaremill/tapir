package sttp.tapir.client.sttp4

import akka.actor.ActorSystem
import cats.effect.IO
import sttp.capabilities.WebSockets
import sttp.capabilities.akka.AkkaStreams
import sttp.client4._
import sttp.client4.akkahttp.AkkaHttpBackend
import sttp.tapir.client.tests.ClientTests
import sttp.tapir.{DecodeResult, Endpoint}

import scala.concurrent.Future

abstract class SttpAkkaClientTests[R >: WebSockets with AkkaStreams] extends ClientTests[R] {
  implicit val actorSystem: ActorSystem = ActorSystem("tests")
  val backend: Backend[Future] = AkkaHttpBackend.usingActorSystem(actorSystem)
  def wsToPipe: WebSocketToPipe[R]

  override def send[A, I, E, O](
      e: Endpoint[A, I, E, O, R],
      port: Port,
      securityArgs: A,
      args: I,
      scheme: String = "http"
  ): IO[Either[E, O]] = {
    implicit val wst: WebSocketToPipe[R] = wsToPipe
    IO.fromFuture(
      IO(
        SttpClientInterpreter()
          .toSecureRequestThrowDecodeFailures(e, Some(uri"$scheme://localhost:$port"))
          .apply(securityArgs)
          .apply(args)
          .send(backend)
          .map(_.body)
      )
    )
  }

  override def safeSend[A, I, E, O](
      e: Endpoint[A, I, E, O, R],
      port: Port,
      securityArgs: A,
      args: I
  ): IO[DecodeResult[Either[E, O]]] = {
    implicit val wst: WebSocketToPipe[R] = wsToPipe
    IO.fromFuture(
      IO(
        SttpClientInterpreter()
          .toSecureRequest(e, Some(uri"http://localhost:$port"))
          .apply(securityArgs)
          .apply(args)
          .send(backend)
          .map(_.body)
      )
    )
  }
}
