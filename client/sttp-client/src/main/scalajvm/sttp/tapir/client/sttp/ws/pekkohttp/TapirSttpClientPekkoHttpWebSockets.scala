package sttp.tapir.client.sttp.ws.pekkohttp

import sttp.capabilities.WebSockets
import sttp.capabilities.pekko.PekkoStreams
import sttp.tapir.client.sttp.WebSocketToPipe

import scala.concurrent.ExecutionContext

trait TapirSttpClientPekkoHttpWebSockets {
  implicit def webSocketsSupportedForPekkoStreams(implicit ec: ExecutionContext): WebSocketToPipe[PekkoStreams with WebSockets] =
    new WebSocketToPekkoPipe[PekkoStreams with WebSockets]
}
