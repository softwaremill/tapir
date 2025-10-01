// {cat=WebSocket; effects=ZIO; server=ZIO HTTP}: Describe and implement a WebSocket endpoint, being notified on the server-side that a client closed the socket, using a custom codec

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.45
//> using dep com.softwaremill.sttp.tapir::tapir-zio-http-server:1.11.45

package sttp.tapir.examples.websocket

import sttp.capabilities.WebSockets
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.generic.auto.*
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.ztapir.*
import sttp.tapir.{Codec, CodecFormat, DecodeResult, PublicEndpoint}
import sttp.ws.WebSocketFrame
import zio.http.{Response as ZioHttpResponse, Routes, Server}
import zio.stream.Stream
import zio.{Console, ExitCode, URIO, ZIO, ZIOAppDefault, ZLayer}

// After running, try opening a ws connection to ws://localhost:8080/ws, sending some text messages, and then closing
// from the client-side.

object WebSocketZioHttpCustomCodecServer extends ZIOAppDefault:
  enum ClientMessage:
    case Leave
    case Message(text: String)

  val wsEndpoint: PublicEndpoint[Unit, Unit, ZioStreams.Pipe[ClientMessage, Option[String]], ZioStreams & WebSockets] =
    endpoint.get
      .in("ws")
      .out(
        webSocketBody[ClientMessage, CodecFormat.TextPlain, Option[String], CodecFormat.TextPlain](ZioStreams)
          // the schema for the `ClientMessage` type is derived as non-optional, that's why we need to explicitly
          // request that close frames should be passed for decoding as well
          // the response type is optional, so we don't need to do that, and a `None` will be encoded as a close
          // frame using the default codec
          .decodeCloseRequests(true)
      )

  val wsServerEndpoint = wsEndpoint.zServerLogic[Any](_ =>
    ZIO.succeed { (clientMessageStream: Stream[Throwable, ClientMessage]) =>
      clientMessageStream.mapZIO {
        case ClientMessage.Leave => Console.printLine("Received a 'leave' message, the socket was closed by the client").map(_ => None)
        case ClientMessage.Message(text) => Console.printLine(s"Received: '$text' message").map(_ => Some("OK"))
      }
    }
  )

  // as we are using a custom high-level type for representing incoming requests from the client, we need to provide
  // a codec which knows how to translate WebSocket frames to instances of ClientMessage
  given wsFrameCodec: Codec[WebSocketFrame, ClientMessage, CodecFormat.TextPlain] =
    val baseStringCodec = Codec.textWebSocketFrame[String, CodecFormat.TextPlain]

    Codec.fromDecodeAndMeta[WebSocketFrame, ClientMessage, CodecFormat.TextPlain](CodecFormat.TextPlain()) {
      case WebSocketFrame.Close(code, reason) => DecodeResult.Value(ClientMessage.Leave)
      case frame                              => baseStringCodec.decode(frame).map(ClientMessage.Message.apply(_))
    } {
      case ClientMessage.Leave        => WebSocketFrame.close
      case ClientMessage.Message(msg) => baseStringCodec.encode(msg)
    }

  // interpreting the endpoints & exposing them
  val routes: Routes[Any, ZioHttpResponse] = ZioHttpInterpreter().toHttp(List(wsServerEndpoint))

  override def run: URIO[Any, ExitCode] =
    Server
      .serve(routes)
      .provide(ZLayer.succeed(Server.Config.default.port(8080)), Server.live)
      .exitCode
