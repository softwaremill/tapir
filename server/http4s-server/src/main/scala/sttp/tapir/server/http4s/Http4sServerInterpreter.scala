package sttp.tapir.server.http4s

import cats.data.OptionT
import cats.effect.{Concurrent, ContextShift, Sync, Timer}
import cats.~>
import org.http4s.{Http, HttpRoutes}
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
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
}

object Http4sServerInterpreter extends Http4sServerInterpreter
