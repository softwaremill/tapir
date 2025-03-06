package sttp.tapir.client.sttp4.ws

import cats.effect.IO
import org.apache.pekko.actor.ActorSystem
import sttp.capabilities.WebSockets
import sttp.capabilities.pekko.PekkoStreams
import sttp.client4._
import sttp.client4.pekkohttp.PekkoHttpBackend
import sttp.tapir.client.sttp4.WebSocketToPipe
import sttp.tapir.client.sttp4.ws.WebSocketSttpClientInterpreter
import sttp.tapir.client.tests.ClientTests
import sttp.tapir.{DecodeResult, Endpoint}

import scala.concurrent.Future

abstract class WebSocketSttpClientPekkoTestsSender extends ClientTests[WebSockets with PekkoStreams] {
  implicit val actorSystem: ActorSystem = ActorSystem("tests")
  val backend: WebSocketBackend[Future] = PekkoHttpBackend.usingActorSystem(actorSystem)
  def wsToPipe: WebSocketToPipe[WebSockets with PekkoStreams]

  // only web socket tests
  override def send[A, I, E, O](
      e: Endpoint[A, I, E, O, WebSockets with PekkoStreams],
      port: Port,
      securityArgs: A,
      args: I,
      scheme: String = "http"
  ): IO[Either[E, O]] = {
    implicit val wst: WebSocketToPipe[WebSockets with PekkoStreams] = wsToPipe
    IO.fromFuture(
      IO {
        WebSocketSttpClientInterpreter()
          .toSecureRequestThrowDecodeFailures[Future, A, I, E, O, WebSockets with PekkoStreams](e, Some(uri"$scheme://localhost:$port"))
          .apply(securityArgs)
          .apply(args)
          .send(backend)
          .map(_.body)
      }
    )
  }

  override def safeSend[A, I, E, O](
      e: Endpoint[A, I, E, O, WebSockets with PekkoStreams],
      port: Port,
      securityArgs: A,
      args: I
  ): IO[DecodeResult[Either[E, O]]] = {
    implicit val wst: WebSocketToPipe[WebSockets with PekkoStreams] = wsToPipe
    IO.fromFuture(
      IO {
        WebSocketSttpClientInterpreter()
          .toSecureRequest(e, Some(uri"http://localhost:$port"))
          .apply(securityArgs)
          .apply(args)
          .send(backend)
          .map(_.body)
      }
    )
  }
}
