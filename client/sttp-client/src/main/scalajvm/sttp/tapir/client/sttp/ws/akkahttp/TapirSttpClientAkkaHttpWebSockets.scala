package sttp.tapir.client.sttp.ws.akkahttp

import sttp.capabilities.akka.AkkaStreams
import sttp.capabilities.{Effect, WebSockets}
import sttp.tapir.client.sttp.WebSocketToPipe

import scala.concurrent.{ExecutionContext, Future}

trait TapirSttpClientAkkaHttpWebSockets {
  implicit def webSocketsSupportedForFs2Streams(implicit ec: ExecutionContext): WebSocketToPipe[AkkaStreams with WebSockets] =
    new WebSocketToAkkaPipe[AkkaStreams with WebSockets]
  implicit def webSocketsSupportedForFs2StreamsAndEffect(implicit
      ec: ExecutionContext
  ): WebSocketToPipe[Effect[Future] with AkkaStreams with WebSockets] =
    new WebSocketToAkkaPipe[Effect[Future] with AkkaStreams with WebSockets]
}
