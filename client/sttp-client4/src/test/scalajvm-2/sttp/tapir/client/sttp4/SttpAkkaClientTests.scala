//package sttp.tapir.client.sttp4
//
//import sttp.tapir.client.sttp4.ws.WebSocketSttpClientInterpreter
//import akka.actor.ActorSystem
//import cats.effect.IO
//import sttp.capabilities.WebSockets
//import sttp.capabilities.akka.AkkaStreams
//import sttp.client4._
//import sttp.client4.akkahttp.AkkaHttpBackend
//import sttp.tapir.client.tests.ClientTests
//import sttp.tapir.{DecodeResult, Endpoint}
//
//import scala.concurrent.Future
//
//abstract class SttpAkkaClientTests[R <: WebSockets with AkkaStreams] extends ClientTests[R] {
//  implicit val actorSystem: ActorSystem = ActorSystem("tests")
//  val backend: WebSocketBackend[Future] = AkkaHttpBackend.usingActorSystem(actorSystem)
//  def wsToPipe: WebSocketToPipe[R]
//
//
//  // used only in web socket tests
//  override def send[A, I, E, O](
//      e: Endpoint[A, I, E, O, R],
//      port: Port,
//      securityArgs: A,
//      args: I,
//      scheme: String = "http"
//  ): IO[Either[E, O]] = {
//    implicit val wst: WebSocketToPipe[R] = wsToPipe
//    IO.fromFuture(
//      IO {
//        WebSocketSttpClientInterpreter()
//          .toSecureRequestThrowDecodeFailures(e, Some(uri"$scheme://localhost:$port"))
//          .apply(securityArgs)
//          .apply(args)
//          .send(backend)
//          .map(_.body)
//      }
//    )
//  }
//
//  override def safeSend[A, I, E, O](
//      e: Endpoint[A, I, E, O, R],
//      port: Port,
//      securityArgs: A,
//      args: I
//  ): IO[DecodeResult[Either[E, O]]] = {
//    implicit val wst: WebSocketToPipe[R] = wsToPipe
//    IO.fromFuture(
//      IO {
//        WebSocketSttpClientInterpreter()
//          .toSecureRequest(e, Some(uri"http://localhost:$port"))
//          .apply(securityArgs)
//          .apply(args)
//          .send(backend)
//          .map(_.body)
//      }
//    )
//  }
//}
