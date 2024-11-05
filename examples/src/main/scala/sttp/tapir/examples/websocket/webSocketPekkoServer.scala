// {cat=WebSocket; effects=Future; server=Pekko HTTP}: Describe and implement a WebSocket endpoint

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-pekko-http-server:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-asyncapi-docs:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-json-circe:1.11.8
//> using dep com.softwaremill.sttp.apispec::asyncapi-circe-yaml:0.10.0
//> using dep com.softwaremill.sttp.client3::pekko-http-backend:3.9.8

package sttp.tapir.examples.websocket

import io.circe.generic.auto.*
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.stream.scaladsl.Flow
import sttp.apispec.asyncapi.Server
import sttp.apispec.asyncapi.circe.yaml.*
import sttp.capabilities.WebSockets
import sttp.capabilities.pekko.PekkoStreams
import sttp.client3.*
import sttp.client3.pekkohttp.PekkoHttpBackend
import sttp.tapir.*
import sttp.tapir.docs.asyncapi.AsyncAPIInterpreter
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.pekkohttp.PekkoHttpServerInterpreter
import sttp.ws.WebSocket

import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}

@main def webSocketPekkoServer(): Unit =
  case class Response(hello: String)

  // The web socket endpoint: GET /ping.
  // We need to provide both the type & media type for the requests, and responses. Here, the requests will be
  // strings, and responses will be returned as json.
  val wsEndpoint: PublicEndpoint[Unit, Unit, Flow[String, Response, Any], PekkoStreams & WebSockets] =
    endpoint.get.in("ping").out(webSocketBody[String, CodecFormat.TextPlain, Response, CodecFormat.Json](PekkoStreams))

  implicit val actorSystem: ActorSystem = ActorSystem()
  import actorSystem.dispatcher

  // Implementation of the web socket: a flow which echoes incoming messages
  val wsRoute: Route =
    PekkoHttpServerInterpreter().toRoute(
      wsEndpoint.serverLogicSuccess(_ => Future.successful(Flow.fromFunction((in: String) => Response(in)): Flow[String, Response, Any]))
    )

  // Documentation
  val apiDocs = AsyncAPIInterpreter().toAsyncAPI(wsEndpoint, "JSON echo", "1.0", List("dev" -> Server("localhost:8080", "ws"))).toYaml
  println(s"Paste into https://playground.asyncapi.io/ to see the docs for this endpoint:\n$apiDocs")

  // Starting the server
  val bindAndCheck = Http()
    .newServerAt("localhost", 8080)
    .bindFlow(wsRoute)
    .flatMap { binding =>
      // We could have interpreted wsEndpoint as a client, but here we are using sttp client directly
      val backend: SttpBackend[Future, WebSockets] = PekkoHttpBackend.usingActorSystem(actorSystem)
      // Client which interacts with the web socket
      basicRequest
        .response(asWebSocket { (ws: WebSocket[Future]) =>
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
        .map(_ => binding)
    }

  val _ = Await.result(bindAndCheck.flatMap(_.terminate(1.minute)).flatMap(_ => actorSystem.terminate()), 1.minute)
