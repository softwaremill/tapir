package sttp.tapir.examples2.websocket

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Flow
import io.circe.generic.auto._
import sttp.tapir.generic.auto._
import sttp.capabilities.WebSockets
import sttp.capabilities.akka.AkkaStreams
import sttp.client3._
import sttp.client3.akkahttp.AkkaHttpBackend
import sttp.apispec.asyncapi.Server
import sttp.apispec.asyncapi.circe.yaml._
import sttp.tapir._
import sttp.tapir.docs.asyncapi.AsyncAPIInterpreter
import sttp.tapir.json.circe._
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter
import sttp.ws.WebSocket

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object WebSocketAkkaServer extends App {
  case class Response(hello: String)

  // The web socket endpoint: GET /ping.
  // We need to provide both the type & media type for the requests, and responses. Here, the requests will be
  // strings, and responses will be returned as json.
  val wsEndpoint: PublicEndpoint[Unit, Unit, Flow[String, Response, Any], AkkaStreams with WebSockets] =
    endpoint.get.in("ping").out(webSocketBody[String, CodecFormat.TextPlain, Response, CodecFormat.Json](AkkaStreams))

  implicit val actorSystem: ActorSystem = ActorSystem()
  import actorSystem.dispatcher

  // Implementation of the web socket: a flow which echoes incoming messages
  val wsRoute: Route =
    AkkaHttpServerInterpreter().toRoute(
      wsEndpoint.serverLogicSuccess(_ => Future.successful(Flow.fromFunction((in: String) => Response(in)): Flow[String, Response, Any]))
    )

  // Documentation
  val apiDocs = AsyncAPIInterpreter().toAsyncAPI(wsEndpoint, "JSON echo", "1.0", List("dev" -> Server("localhost:8080", "ws"))).toYaml
  println(s"Paste into https://playground.asyncapi.io/ to see the docs for this endpoint:\n$apiDocs")

  // Starting the server
  val bindAndCheck = Http()
    .newServerAt("localhost", 8080)
    .bindFlow(wsRoute)
    .flatMap { _ =>
      // We could have interpreted wsEndpoint as a client, but here we are using sttp client directly
      val backend: SttpBackend[Future, WebSockets] = AkkaHttpBackend.usingActorSystem(actorSystem)
      // Client which interacts with the web socket
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
