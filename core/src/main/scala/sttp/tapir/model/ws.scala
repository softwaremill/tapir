package sttp.tapir.model

import sttp.tapir.DecodeResult
import sttp.ws.{WebSocketException, WebSocketFrame}

class UnsupportedWebSocketFrameException(f: WebSocketFrame) extends WebSocketException(s"Unsupported web socket frame: $f")

class WebSocketFrameDecodeFailure(f: WebSocketFrame, failure: DecodeResult.Failure)
    extends WebSocketException(s"Cannot decode frame: $f, due to: $failure")
