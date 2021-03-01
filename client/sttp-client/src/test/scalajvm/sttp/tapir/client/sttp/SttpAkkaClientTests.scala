package sttp.tapir.client.sttp

import akka.actor.ActorSystem
import cats.effect.{ContextShift, IO}
import sttp.capabilities.WebSockets
import sttp.capabilities.akka.AkkaStreams
import sttp.client3._
import sttp.client3.akkahttp.AkkaHttpBackend
import sttp.tapir.client.tests.ClientTests
import sttp.tapir.{DecodeResult, Endpoint}

import scala.concurrent.ExecutionContext

abstract class SttpAkkaClientTests[R >: WebSockets with AkkaStreams] extends ClientTests[R] {
  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.Implicits.global)
  implicit val actorSystem = ActorSystem("tests")
  val backend = AkkaHttpBackend.usingActorSystem(actorSystem)
  def wsToPipe: WebSocketToPipe[R]

  override def send[I, E, O, FN[_]](e: Endpoint[I, E, O, R], port: Port, args: I, scheme: String = "http"): IO[Either[E, O]] = {
    implicit val wst: WebSocketToPipe[R] = wsToPipe
    IO.fromFuture(
      IO(SttpClientInterpreter.toRequestThrowDecodeFailures(e, Some(uri"$scheme://localhost:$port")).apply(args).send(backend).map(_.body))
    )
  }

  override def safeSend[I, E, O, FN[_]](
      e: Endpoint[I, E, O, R],
      port: Port,
      args: I
  ): IO[DecodeResult[Either[E, O]]] = {
    implicit val wst: WebSocketToPipe[R] = wsToPipe
    IO.fromFuture(IO(SttpClientInterpreter.toRequest(e, Some(uri"http://localhost:$port")).apply(args).send(backend).map(_.body)))
  }
}
