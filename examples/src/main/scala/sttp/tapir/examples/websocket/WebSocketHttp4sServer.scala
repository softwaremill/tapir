// {cat=WebSocket; effects=cats-effect; server=http4s; json=circe; docs=AsyncAPI}: Describe and implement a WebSocket endpoint

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-http4s-server:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-asyncapi-docs:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-json-circe:1.11.8
//> using dep com.softwaremill.sttp.apispec::asyncapi-circe-yaml:0.10.0
//> using dep com.softwaremill.sttp.client3::async-http-client-backend-fs2:3.10.1
//> using dep org.http4s::http4s-blaze-server:0.23.16

package sttp.tapir.examples.websocket

import cats.effect.{ExitCode, IO, IOApp}
import io.circe.generic.auto.*
import fs2.*
import org.http4s.HttpRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import org.http4s.server.websocket.WebSocketBuilder2
import sttp.apispec.asyncapi.Server
import sttp.apispec.asyncapi.circe.yaml.*
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.*
import sttp.client3.asynchttpclient.fs2.AsyncHttpClientFs2Backend
import sttp.tapir.*
import sttp.tapir.docs.asyncapi.AsyncAPIInterpreter
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.ws.WebSocket

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*

object WebSocketHttp4sServer extends IOApp:

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  case class CountResponse(received: Int)

  // The web socket endpoint: GET /count.
  // We need to provide both the type & media type for the requests, and responses. Here, the requests will be
  // byte arrays, and responses will be returned as json.
  val wsEndpoint: PublicEndpoint[Unit, Unit, Pipe[IO, String, CountResponse], Fs2Streams[IO] & WebSockets] =
    endpoint.get.in("count").out(webSocketBody[String, CodecFormat.TextPlain, CountResponse, CodecFormat.Json](Fs2Streams[IO]))

  // A pipe which counts the number of bytes received each second
  val countBytes: Pipe[IO, String, CountResponse] = { in =>
    sealed trait CountAction
    case class AddAction(n: Int) extends CountAction
    case object EmitAction extends CountAction

    sealed trait CountEffect
    case class EmitCount(n: Int) extends CountEffect
    case class IntermediateCount(n: Int) extends CountEffect

    val incomingByteCount = in.map(s => AddAction(s.getBytes().length))
    val everySecond = Stream.awakeEvery[IO](1.second).map(_ => EmitAction)

    incomingByteCount
      .mergeHaltL(everySecond)
      .scan(IntermediateCount(0): CountEffect) {
        case (IntermediateCount(total), AddAction(next)) => IntermediateCount(total + next)
        case (IntermediateCount(total), EmitAction)      => EmitCount(total)
        case (EmitCount(_), AddAction(next))             => IntermediateCount(next)
        case (EmitCount(_), EmitAction)                  => EmitCount(0)
      }
      .collect { case EmitCount(n) =>
        CountResponse(n)
      }
  }

  // Implementing the endpoint's logic, by providing the web socket pipe
  val wsRoutes: WebSocketBuilder2[IO] => HttpRoutes[IO] =
    Http4sServerInterpreter[IO]().toWebSocketRoutes(wsEndpoint.serverLogicSuccess(_ => IO.pure(countBytes)))

  // Documentation
  val apiDocs = AsyncAPIInterpreter().toAsyncAPI(wsEndpoint, "Byte counter", "1.0", List("dev" -> Server("localhost:8080", "ws"))).toYaml
  println(s"Paste into https://playground.asyncapi.io/ to see the docs for this endpoint:\n$apiDocs")

  override def run(args: List[String]): IO[ExitCode] =
    // Starting the server
    BlazeServerBuilder[IO]
      .withExecutionContext(ec)
      .bindHttp(8080, "localhost")
      .withHttpWebSocketApp(wsb => Router("/" -> wsRoutes(wsb)).orNotFound)
      .resource
      .flatMap(_ => AsyncHttpClientFs2Backend.resource[IO]())
      .use { backend =>
        // Client which interacts with the web socket
        basicRequest
          .response(asWebSocket { (ws: WebSocket[IO]) =>
            for {
              _ <- ws.sendText("7 bytes")
              _ <- ws.sendText("7 bytes")
              r1 <- ws.receiveText()
              _ = println(r1)
              _ <- ws.sendText("10   bytes")
              _ <- ws.sendText("12     bytes")
              r2 <- ws.receiveText()
              _ = println(r2)
              _ <- IO.sleep(3.seconds)
              _ <- ws.sendText("7 bytes")
              r3 <- ws.receiveText()
              r4 <- ws.receiveText()
              r5 <- ws.receiveText()
              r6 <- ws.receiveText()
              _ = println(r3)
              _ = println(r4)
              _ = println(r5)
              _ = println(r6)
            } yield ()
          })
          .get(uri"ws://localhost:8080/count")
          .send(backend)
          .map(_ => println("Counting complete, bye!"))
      }
      .as(ExitCode.Success)
