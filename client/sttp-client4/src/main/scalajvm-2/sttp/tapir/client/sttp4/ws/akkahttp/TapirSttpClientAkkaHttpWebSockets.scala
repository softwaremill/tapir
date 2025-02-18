package sttp.tapir.client.sttp4.ws.akkahttp

import sttp.capabilities.WebSockets
import sttp.capabilities.akka.AkkaStreams
import sttp.tapir.client.sttp4.WebSocketToPipe

import scala.concurrent.ExecutionContext

trait TapirSttpClientAkkaHttpWebSockets {
  implicit def webSocketsSupportedForAkkaStreams(implicit ec: ExecutionContext): WebSocketToPipe[AkkaStreams with WebSockets] =
    new WebSocketToAkkaPipe[AkkaStreams with WebSockets]
}
