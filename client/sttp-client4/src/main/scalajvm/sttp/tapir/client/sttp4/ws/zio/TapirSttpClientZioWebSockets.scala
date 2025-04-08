package sttp.tapir.client.sttp4.ws.zio

import sttp.capabilities.WebSockets
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.client.sttp4.WebSocketToPipe

trait TapirSttpClientZioWebSockets {
  implicit val webSocketsSupportedForZioStreams: WebSocketToPipe[ZioStreams with WebSockets] =
    new WebSocketToZioPipe[ZioStreams with WebSockets]
}
