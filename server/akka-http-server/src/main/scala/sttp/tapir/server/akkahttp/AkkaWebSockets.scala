package sttp.tapir.server.akkahttp

import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import sttp.capabilities.akka.AkkaStreams
import sttp.tapir.model.WebSocketFrameDecodeFailure
import sttp.tapir.{DecodeResult, WebSocketBodyOutput}
import sttp.ws.{WebSocketClosed, WebSocketFrame}

import scala.concurrent.{ExecutionContext, Future}

private[akkahttp] object AkkaWebSockets {
  def pipeToBody[REQ, RESP](
      pipe: Flow[REQ, RESP, Any],
      o: WebSocketBodyOutput[Flow[REQ, RESP, Any], REQ, RESP, _, AkkaStreams]
  )(implicit ec: ExecutionContext, mat: Materializer): Flow[Message, Message, Any] = {
    Flow[Message]
      .mapAsync(1)(messageToFrame(_))
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

  private def messageToFrame(
      m: Message
  )(implicit ec: ExecutionContext, mat: Materializer): Future[WebSocketFrame.Data[_]] =
    m match {
      case msg: TextMessage =>
        msg.textStream.runFold("")(_ + _).map(t => WebSocketFrame.text(t))
      case msg: BinaryMessage =>
        msg.dataStream.runFold(ByteString.empty)(_ ++ _).map(b => WebSocketFrame.binary(b.toArrayUnsafe()))
    }

  private def frameToMessage(w: WebSocketFrame): Option[Message] = {
    w match {
      case WebSocketFrame.Text(p, _, _)   => Some(TextMessage(p))
      case WebSocketFrame.Binary(p, _, _) => Some(BinaryMessage(ByteString.fromArrayUnsafe(p)))
      case WebSocketFrame.Ping(_)         => None
      case WebSocketFrame.Pong(_)         => None
      case WebSocketFrame.Close(_, _)     => throw WebSocketClosed(None)
    }
  }
}
