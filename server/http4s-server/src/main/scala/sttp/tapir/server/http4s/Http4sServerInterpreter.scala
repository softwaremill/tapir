package sttp.tapir.server.http4s

import cats.data.OptionT
import cats.effect.{Concurrent, ContextShift, Sync, Timer}
import cats.~>
import org.http4s.{Http, HttpRoutes}
import sttp.capabilities.WebSockets
import fs2.{Stream, text}
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.sse.ServerSentEvent
import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint

import scala.reflect.ClassTag

trait Http4sServerInterpreter {
  def toHttp[I, E, O, F[_], G[_]](e: Endpoint[I, E, O, Fs2Streams[F] with WebSockets])(t: F ~> G)(logic: I => G[Either[E, O]])(implicit
      serverOptions: Http4sServerOptions[F],
      gs: Sync[G],
      fs: Concurrent[F],
      fcs: ContextShift[F],
      timer: Timer[F]
  ): Http[OptionT[G, *], F] = {
    new EndpointToHttp4sServer(serverOptions).toHttp(t, e.serverLogic(logic))
  }

  def toHttpRecoverErrors[I, E, O, F[_], G[_]](e: Endpoint[I, E, O, Fs2Streams[F] with WebSockets])(t: F ~> G)(logic: I => G[O])(implicit
      serverOptions: Http4sServerOptions[F],
      gs: Sync[G],
      fs: Concurrent[F],
      fcs: ContextShift[F],
      eIsThrowable: E <:< Throwable,
      eClassTag: ClassTag[E],
      timer: Timer[F]
  ): Http[OptionT[G, *], F] = {
    new EndpointToHttp4sServer(serverOptions).toHttp(t, e.serverLogicRecoverErrors(logic))
  }

  def toRoutes[I, E, O, F[_]](e: Endpoint[I, E, O, Fs2Streams[F] with WebSockets])(
      logic: I => F[Either[E, O]]
  )(implicit serverOptions: Http4sServerOptions[F], fs: Concurrent[F], fcs: ContextShift[F], timer: Timer[F]): HttpRoutes[F] = {
    new EndpointToHttp4sServer(serverOptions).toRoutes(e.serverLogic(logic))
  }

  def toRouteRecoverErrors[I, E, O, F[_]](e: Endpoint[I, E, O, Fs2Streams[F] with WebSockets])(logic: I => F[O])(implicit
      serverOptions: Http4sServerOptions[F],
      fs: Concurrent[F],
      fcs: ContextShift[F],
      eIsThrowable: E <:< Throwable,
      eClassTag: ClassTag[E],
      timer: Timer[F]
  ): HttpRoutes[F] = {
    new EndpointToHttp4sServer(serverOptions).toRoutes(e.serverLogicRecoverErrors(logic))
  }

  def toHttp[I, E, O, F[_], G[_]](se: ServerEndpoint[I, E, O, Fs2Streams[F] with WebSockets, G])(
      t: F ~> G
  )(implicit
      serverOptions: Http4sServerOptions[F],
      gs: Sync[G],
      fs: Concurrent[F],
      fcs: ContextShift[F],
      timer: Timer[F]
  ): Http[OptionT[G, *], F] =
    new EndpointToHttp4sServer(serverOptions).toHttp(t, se)

  def toRoutes[I, E, O, F[_]](
      se: ServerEndpoint[I, E, O, Fs2Streams[F] with WebSockets, F]
  )(implicit serverOptions: Http4sServerOptions[F], fs: Concurrent[F], fcs: ContextShift[F], timer: Timer[F]): HttpRoutes[F] =
    new EndpointToHttp4sServer(serverOptions).toRoutes(se)

  def toHttp[F[_], G[_]](serverEndpoints: List[ServerEndpoint[_, _, _, Fs2Streams[F] with WebSockets, G]])(t: F ~> G)(implicit
      serverOptions: Http4sServerOptions[F],
      gs: Sync[G],
      fs: Concurrent[F],
      fcs: ContextShift[F],
      timer: Timer[F]
  ): Http[OptionT[G, *], F] = {
    new EndpointToHttp4sServer[F](serverOptions).toHttp(t)(serverEndpoints)
  }

  def toRoutes[F[_]](serverEndpoints: List[ServerEndpoint[_, _, _, Fs2Streams[F] with WebSockets, F]])(implicit
      serverOptions: Http4sServerOptions[F],
      fs: Concurrent[F],
      fcs: ContextShift[F],
      timer: Timer[F]
  ): HttpRoutes[F] = {
    new EndpointToHttp4sServer(serverOptions).toRoutes(serverEndpoints)
  }

  def serialiseSSEToBytes[F[_]](streams: Fs2Streams[F]): Stream[F, ServerSentEvent] => streams.BinaryStream = sseStream => {
    sseStream
      .map(sse => {
        s"${composeSSE(sse)}\n\n"
      })
      .through(text.utf8Encode)
  }

  private def composeSSE(sse: ServerSentEvent) = {
    val data = sse.data.map(_.split("\n")).map(_.map(line => Some(s"data: $line"))).getOrElse(Array.empty)
    val event = sse.eventType.map(event => s"event: $event")
    val id = sse.id.map(id => s"id: $id")
    val retry = sse.retry.map(retryCount => s"retry: $retryCount")
    (data :+ event :+ id :+ retry).flatten.mkString("\n")
  }

  def parseBytesToSSE[F[_]](streams: Fs2Streams[F]): streams.BinaryStream => Stream[F, ServerSentEvent] = stream => {
    stream
      .through(text.utf8Decode[F])
      .through(text.lines[F])
      .split(_.isEmpty)
      .filter(_.nonEmpty)
      .map(_.toList)
      .map(ServerSentEvent.parse)
  }
}

object Http4sServerInterpreter extends Http4sServerInterpreter
