// {cat=WebSocket; effects=ZIO; server=ZIO HTTP; json=ZIO JSON}: Describe and implement a WebSocket endpoint, accepting and returning JSON messages

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.13.7
//> using dep com.softwaremill.sttp.tapir::tapir-json-zio:1.13.7
//> using dep com.softwaremill.sttp.tapir::tapir-zio-http-server:1.13.7

package sttp.tapir.examples.websocket

import sttp.capabilities.WebSockets
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.*
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.ztapir.*
import sttp.tapir.{Codec, CodecFormat, PublicEndpoint}
import zio.http.{Response as ZioHttpResponse, Routes, Server}
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}
import zio.stream.Stream
import zio.{ExitCode, URIO, ZIO, ZIOAppDefault, ZLayer}

// After running, try opening a ws connection to ws://localhost:8080/ws, sending some text messages, and then closing
// from the client-side.

object WebSocketZioHttpJsonServer extends ZIOAppDefault:
  case class Input(a: Int, b: Int)
  object Input:
    given JsonDecoder[Input] = DeriveJsonDecoder.gen[Input]
    given JsonEncoder[Input] = DeriveJsonEncoder.gen[Input]

  case class Output(c: Int)
  object Output:
    given JsonDecoder[Output] = DeriveJsonDecoder.gen[Output]
    given JsonEncoder[Output] = DeriveJsonEncoder.gen[Output]

  val wsEndpoint: PublicEndpoint[Unit, Unit, ZioStreams.Pipe[Input, Output], ZioStreams & WebSockets] =
    endpoint.get
      .in("ws")
      .out(webSocketBody[Input, CodecFormat.Json, Output, CodecFormat.Json](ZioStreams))

  val wsServerEndpoint = wsEndpoint.zServerLogic[Any](_ =>
    ZIO.succeed { (clientMessageStream: Stream[Throwable, Input]) =>
      clientMessageStream.map { input => Output(input.a + input.b) }
    }
  )

  // interpreting the endpoints & exposing them
  val routes: Routes[Any, ZioHttpResponse] = ZioHttpInterpreter().toHttp(List(wsServerEndpoint))

  override def run: URIO[Any, ExitCode] =
    Server
      .serve(routes)
      .provide(ZLayer.succeed(Server.Config.default.port(8080)), Server.live)
      .exitCode
