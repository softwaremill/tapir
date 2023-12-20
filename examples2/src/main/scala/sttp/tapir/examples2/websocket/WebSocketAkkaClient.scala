package sttp.tapir.examples2.websocket

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Sink, Source}
import io.circe.generic.auto._
import sttp.capabilities.WebSockets
import sttp.capabilities.akka.AkkaStreams
import sttp.client3._
import sttp.client3.akkahttp.AkkaHttpBackend
import sttp.tapir._
import sttp.tapir.client.sttp.SttpClientInterpreter
import sttp.tapir.json.circe._
import sttp.tapir.generic.auto._
import sttp.tapir.client.sttp.ws.akkahttp._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
object WebSocketAkkaClient extends App {
  case class TestMessage(text: String, counter: Int)

  val jsonEchoWsEndpoint: PublicEndpoint[Unit, Unit, Flow[TestMessage, TestMessage, Any], AkkaStreams with WebSockets] =
    endpoint.get.out(webSocketBody[TestMessage, CodecFormat.Json, TestMessage, CodecFormat.Json](AkkaStreams))

  implicit val actorSystem: ActorSystem = ActorSystem()
  val backend = AkkaHttpBackend.usingActorSystem(actorSystem)
  val result = SttpClientInterpreter()
    .toClientThrowDecodeFailures(jsonEchoWsEndpoint, Some(uri"wss://echo.websocket.org"), backend)
    .apply(())
    .flatMap {
      case Left(msg) => Future(println(s"Cannot establish web socket: $msg"))
      case Right(serverFlow) =>
        val queue = serverFlow
          .runWith(
            Source(List(TestMessage("msg1", 10), TestMessage("msg2", 20), TestMessage("msg3", 30))),
            Sink.queue()
          )
          ._2

        for {
          response1 <- queue.pull()
          _ = println(s"Response 1: $response1")
          response2 <- queue.pull()
          _ = println(s"Response 2: $response2")
          response3 <- queue.pull()
          _ = println(s"Response 3: $response3")
        } yield queue.cancel()
    }
    .flatMap(_ => actorSystem.terminate())

  Await.result(result, 1.minute)
}
