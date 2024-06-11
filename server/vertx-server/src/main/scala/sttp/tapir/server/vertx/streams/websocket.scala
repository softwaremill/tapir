package sttp.tapir.server.vertx.streams

import sttp.ws.WebSocketFrame

object websocket {
  type Accumulator = Option[Either[Array[Byte], String]]

  def concatenateFrames(acc: Accumulator, frame: WebSocketFrame): (Accumulator, Option[WebSocketFrame]) =
    (acc, frame) match {
      case (None, f: WebSocketFrame.Ping) =>
        (None, Some(f))
      case (None, f: WebSocketFrame.Pong) =>
        (None, Some(f))
      case (None, f: WebSocketFrame.Close) =>
        (None, Some(f))
      case (None, f: WebSocketFrame.Data[_]) if f.finalFragment =>
        (None, Some(f))
      case (None, f: WebSocketFrame.Text) =>
        (Some(Right(f.payload)), None)
      case (None, f: WebSocketFrame.Binary) =>
        (Some(Left(f.payload)), None)
      case (Some(Left(acc)), f: WebSocketFrame.Binary) if f.finalFragment =>
        (None, Some(f.copy(payload = acc ++ f.payload)))
      case (Some(Left(acc)), f: WebSocketFrame.Binary) if !f.finalFragment =>
        (Some(Left(acc ++ f.payload)), None)
      case (Some(Right(acc)), f: WebSocketFrame.Text) if f.finalFragment =>
        (None, Some(f.copy(payload = acc + f.payload)))
      case (Some(Right(acc)), f: WebSocketFrame.Text) if !f.finalFragment =>
        (Some(Right(acc + f.payload)), None)
      case (acc, f) =>
        throw new IllegalStateException(s"Cannot accumulate web socket frames. Accumulator: $acc, frame: $f.")
    }

  def isPong(frame: WebSocketFrame): Boolean =
    frame match {
      case WebSocketFrame.Pong(_) => true
      case _                      => false
    }
}
