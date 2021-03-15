package sttp.tapir.server.akkahttp

import akka.NotUsed
import akka.http.scaladsl.server.{Directive, Route}
import akka.stream.scaladsl.{Flow, Framing, Source}
import akka.util.ByteString
import sttp.capabilities.WebSockets
import sttp.capabilities.akka.AkkaStreams
import sttp.model.sse.ServerSentEvent
import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint

import scala.concurrent.Future
import scala.reflect.ClassTag

trait AkkaHttpServerInterpreter {
  def toDirective[I, E, O](e: Endpoint[I, E, O, AkkaStreams with WebSockets])(implicit
      serverOptions: AkkaHttpServerOptions
  ): Directive[(I, Future[Either[E, O]] => Route)] =
    new EndpointToAkkaServer(serverOptions).toDirective(e)

  def toRoute[I, E, O](e: Endpoint[I, E, O, AkkaStreams with WebSockets])(logic: I => Future[Either[E, O]])(implicit
      serverOptions: AkkaHttpServerOptions
  ): Route =
    new EndpointToAkkaServer(serverOptions).toRoute(e.serverLogic(logic))

  def toRouteRecoverErrors[I, E, O](
      e: Endpoint[I, E, O, AkkaStreams with WebSockets]
  )(logic: I => Future[O])(implicit eIsThrowable: E <:< Throwable, eClassTag: ClassTag[E], serverOptions: AkkaHttpServerOptions): Route = {
    new EndpointToAkkaServer(serverOptions).toRoute(e.serverLogicRecoverErrors(logic))
  }

  //

  def toDirective[I, E, O](serverEndpoint: ServerEndpoint[I, E, O, AkkaStreams with WebSockets, Future])(implicit
      serverOptions: AkkaHttpServerOptions
  ): Directive[(I, Future[Either[E, O]] => Route)] =
    new EndpointToAkkaServer(serverOptions).toDirective(serverEndpoint.endpoint)

  def toRoute[I, E, O](serverEndpoint: ServerEndpoint[I, E, O, AkkaStreams with WebSockets, Future])(implicit
      serverOptions: AkkaHttpServerOptions
  ): Route = new EndpointToAkkaServer(serverOptions).toRoute(serverEndpoint)

  //

  def toRoute(serverEndpoints: List[ServerEndpoint[_, _, _, AkkaStreams with WebSockets, Future]])(implicit
      serverOptions: AkkaHttpServerOptions
  ): Route = {
    new EndpointToAkkaServer(serverOptions).toRoute(serverEndpoints)
  }

  def serialiseSSEToBytes: Source[ServerSentEvent, Any] => AkkaStreams.BinaryStream = sseStream =>
    sseStream.map(sse => {
      ByteString(s"${composeSSE(sse)}\n\n")
    })

  private def composeSSE(sse: ServerSentEvent) = {
    val data = sse.data.map(_.split("\n")).map(_.map(line => Some(s"data: $line"))).getOrElse(Array.empty)
    val event = sse.eventType.map(event => s"event: $event")
    val id = sse.id.map(id => s"id: $id")
    val retry = sse.retry.map(retryCount => s"retry: $retryCount")
    (data :+ event :+ id :+ retry).flatten.mkString("\n")
  }

  def parseBytesToSSE: AkkaStreams.BinaryStream => Source[ServerSentEvent, Any] = stream => stream.via(parse)

  private val parse: Flow[ByteString, ServerSentEvent, NotUsed] =
    Framing
      .delimiter(ByteString("\n\n"), maximumFrameLength = Int.MaxValue, allowTruncation = true)
      .map(_.utf8String)
      .map(_.split("\n").toList)
      .map(ServerSentEvent.parse)
}

object AkkaHttpServerInterpreter extends AkkaHttpServerInterpreter
