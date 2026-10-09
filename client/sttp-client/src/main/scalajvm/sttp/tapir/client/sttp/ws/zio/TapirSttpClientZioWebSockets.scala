package sttp.tapir.client.sttp.ws.zio

import sttp.capabilities.WebSockets
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.client.sttp.WebSocketToPipe

trait TapirSttpClientZioWebSockets {
  implicit val webSocketsSupportedForZioStreams: WebSocketToPipe[ZioStreams & WebSockets] =
    new WebSocketToZioPipe[ZioStreams & WebSockets]
}
