package sttp.tapir.client.sttp4.ws

import org.apache.pekko.actor.ActorSystem
import sttp.capabilities.WebSockets
import sttp.capabilities.pekko.PekkoStreams
import sttp.client4._
import sttp.client4.pekkohttp.PekkoHttpBackend
import sttp.tapir.client.sttp4.WebSocketToPipe
import sttp.tapir.client.sttp4.ws.WebSocketSttpClientInterpreter
import sttp.tapir.client.tests.ClientTests
import sttp.tapir.{DecodeResult, Endpoint}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

abstract class WebSocketSttpClientPekkoTestsSender extends ClientTests[WebSockets & PekkoStreams] {
  implicit val actorSystem: ActorSystem = ActorSystem("tests")
  val backend: WebSocketBackend[Future] = PekkoHttpBackend.usingActorSystem(actorSystem)
  def wsToPipe: WebSocketToPipe[WebSockets & PekkoStreams]

  override protected def afterAll(): Unit = {
    super.afterAll()
    Await.result(actorSystem.terminate(), 10.seconds)
  }

  // only web socket tests
  override def send[A, I, E, O](
      e: Endpoint[A, I, E, O, WebSockets & PekkoStreams],
      port: Port,
      securityArgs: A,
      args: I,
      scheme: String = "http"
  ): Future[Either[E, O]] = {
    implicit val wst: WebSocketToPipe[WebSockets & PekkoStreams] = wsToPipe
    WebSocketSttpClientInterpreter()
      .toSecureRequestThrowDecodeFailures[Future, A, I, E, O, WebSockets & PekkoStreams](e, Some(uri"$scheme://localhost:$port"))
      .apply(securityArgs)
      .apply(args)
      .send(backend)
      .map(_.body)
  }

  override def safeSend[A, I, E, O](
      e: Endpoint[A, I, E, O, WebSockets & PekkoStreams],
      port: Port,
      securityArgs: A,
      args: I
  ): Future[DecodeResult[Either[E, O]]] = {
    implicit val wst: WebSocketToPipe[WebSockets & PekkoStreams] = wsToPipe
    WebSocketSttpClientInterpreter()
      .toSecureRequest[Future, A, I, E, O, WebSockets & PekkoStreams](e, Some(uri"http://localhost:$port"))
      .apply(securityArgs)
      .apply(args)
      .send(backend)
      .map(_.body)
  }
}
