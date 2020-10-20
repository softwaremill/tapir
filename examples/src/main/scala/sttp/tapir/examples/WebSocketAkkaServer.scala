package sttp.tapir.examples

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Flow
import io.circe.generic.auto._
import sttp.capabilities.WebSockets
import sttp.capabilities.akka.AkkaStreams
import sttp.client3._
import sttp.client3.akkahttp.AkkaHttpBackend
import sttp.tapir._
import sttp.tapir.json.circe._
import sttp.tapir.server.akkahttp._
import sttp.ws.WebSocket

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object WebSocketAkkaServer extends App {
  case class Response(hello: String)

  // The endpoint: GET /ws.
  // We need to provide both the type & media type for the requests, and responses. Here, the requests will be
  // strings, and responses will be returned as json.
  val wsEndpoint: Endpoint[Unit, Unit, Flow[String, Response, Any], AkkaStreams with WebSockets] =
    endpoint.get.in("ping").out(webSocketBody[String, CodecFormat.TextPlain, Response, CodecFormat.Json](AkkaStreams))

  val wsRoute: Route = wsEndpoint.toRoute(_ => Future.successful(Right(Flow.fromFunction((in: String) => Response(in)))))

  // starting the server
  implicit val actorSystem: ActorSystem = ActorSystem()
  import actorSystem.dispatcher

  val bindAndCheck = Http()
    .newServerAt("localhost", 8080)
    .bindFlow(wsRoute)
    .flatMap { _ =>
      // we could have interpreted wsEndpoint as a client, but here we are using sttp client directly
      val backend: SttpBackend[Future, WebSockets] = AkkaHttpBackend.usingActorSystem(actorSystem)
      basicRequest
        .response(asWebSocket { ws: WebSocket[Future] =>
          for {
            _ <- ws.sendText("world")
            _ <- ws.sendText("there")
            r1 <- ws.receiveText()
            _ = println(r1)
            r2 <- ws.receiveText()
            _ = println(r2)
            _ <- ws.sendText("how are you")
            r3 <- ws.receiveText()
            _ = println(r3)
          } yield ()
        })
        .get(uri"ws://localhost:8080/ping")
        .send(backend)
    }

  Await.result(bindAndCheck.transformWith { r => actorSystem.terminate().transform(_ => r) }, 1.minute)
}
