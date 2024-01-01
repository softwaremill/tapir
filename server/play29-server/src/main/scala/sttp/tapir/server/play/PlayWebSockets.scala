package sttp.tapir.server.play

import akka.stream.scaladsl.Flow
import akka.util.ByteString
import play.api.http.websocket._
import sttp.capabilities.akka.AkkaStreams
import sttp.tapir.model.WebSocketFrameDecodeFailure
import sttp.tapir.{DecodeResult, WebSocketBodyOutput}
import sttp.ws.WebSocketFrame

private[play] object PlayWebSockets {
  def pipeToBody[REQ, RESP](
      pipe: Flow[REQ, RESP, Any],
      o: WebSocketBodyOutput[Flow[REQ, RESP, Any], REQ, RESP, _, AkkaStreams]
  ): Flow[Message, Message, Any] = {
    Flow[Message]
      .map(messageToFrame)
      .collect { case data: WebSocketFrame.Data[_] =>
        data
      }
      .map(f =>
        o.requests.decode(f) match {
          case failure: DecodeResult.Failure => throw new WebSocketFrameDecodeFailure(f, failure)
          case DecodeResult.Value(v)         => v
        }
      )
      .via(pipe)
      .map(o.responses.encode)
      .takeWhile {
        case WebSocketFrame.Close(_, _) => false
        case _                          => true
      }
      .mapConcat(frameToMessage(_).toList)
  }

  private def messageToFrame(m: Message): WebSocketFrame =
    m match {
      case msg: TextMessage   => WebSocketFrame.text(msg.data)
      case msg: BinaryMessage => WebSocketFrame.binary(msg.data.toArray)
      case msg: PingMessage   => WebSocketFrame.Ping(msg.data.toArray)
      case msg: PongMessage   => WebSocketFrame.Pong(msg.data.toArray)
      case msg: CloseMessage  => WebSocketFrame.Close(msg.statusCode.getOrElse(WebSocketFrame.close.statusCode), msg.reason)
    }

  private def frameToMessage(w: WebSocketFrame): Option[Message] = {
    w match {
      case WebSocketFrame.Text(p, _, _)     => Some(TextMessage(p))
      case WebSocketFrame.Binary(p, _, _)   => Some(BinaryMessage(ByteString(p)))
      case WebSocketFrame.Ping(p)           => Some(PingMessage(ByteString(p)))
      case WebSocketFrame.Pong(p)           => Some(PongMessage(ByteString(p)))
      case WebSocketFrame.Close(code, text) => Some(CloseMessage(code, text))
    }
  }
}
