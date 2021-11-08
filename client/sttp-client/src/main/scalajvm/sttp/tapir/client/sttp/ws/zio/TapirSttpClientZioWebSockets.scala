package sttp.tapir.client.sttp.ws.zio

import sttp.capabilities.WebSockets
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.client.sttp.WebSocketToPipe

trait TapirSttpClientZioWebSockets {
  implicit val webSocketsSupportedForZioStreams: WebSocketToPipe[ZioStreams with WebSockets] =
    new WebSocketToZioPipe[ZioStreams with WebSockets]
}
