package sttp.tapir.server.http4s

import cats.{Monad, ~>}
import cats.data.OptionT
import cats.effect.{Concurrent, ContextShift, Sync}
import cats.syntax.all._
import org.http4s.{Http, HttpRoutes}
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint

import scala.reflect.ClassTag

// We need Concurrent[F] because we are creating an intermediate queue for handling websockets. It might be possible
// to revert to Sync[F] after the WS changes in http4s 1.
trait TapirHttp4sServer {
  implicit class RichHttp4sHttpEndpoint[I, E, O, F[_]](e: Endpoint[I, E, O, Fs2Streams[F] with WebSockets]) {
    def toHttp[G[_]](t: F ~> G)(logic: I => G[Either[E, O]])(implicit
        serverOptions: Http4sServerOptions[F],
        gs: Sync[G],
        fs: Concurrent[F],
        fcs: ContextShift[F]
    ): Http[OptionT[G, *], F] = {
      new EndpointToHttp4sServer(serverOptions).toHttp(t, e.serverLogic(logic))
    }

    def toHttpRecoverErrors[G[_]](t: F ~> G)(logic: I => G[O])(implicit
        serverOptions: Http4sServerOptions[F],
        gs: Sync[G],
        fs: Concurrent[F],
        fcs: ContextShift[F],
        eIsThrowable: E <:< Throwable,
        eClassTag: ClassTag[E]
    ): Http[OptionT[G, *], F] = {
      new EndpointToHttp4sServer(serverOptions).toHttp(t, e.serverLogicRecoverErrors(logic))
    }

    def toRoutes(
        logic: I => F[Either[E, O]]
    )(implicit serverOptions: Http4sServerOptions[F], fs: Concurrent[F], fcs: ContextShift[F]): HttpRoutes[F] = {
      new EndpointToHttp4sServer(serverOptions).toRoutes(e.serverLogic(logic))
    }

    def toRouteRecoverErrors(logic: I => F[O])(implicit
        serverOptions: Http4sServerOptions[F],
        fs: Concurrent[F],
        fcs: ContextShift[F],
        eIsThrowable: E <:< Throwable,
        eClassTag: ClassTag[E]
    ): HttpRoutes[F] = {
      new EndpointToHttp4sServer(serverOptions).toRoutes(e.serverLogicRecoverErrors(logic))
    }
  }

  implicit class RichHttp4sServerEndpoint0[I, E, O, F[_], G[_]](se: ServerEndpoint[I, E, O, Fs2Streams[F] with WebSockets, G]) {
    def toHttp(
        t: F ~> G
    )(implicit
        serverOptions: Http4sServerOptions[F],
        gs: Sync[G],
        fs: Concurrent[F],
        fcs: ContextShift[F]
    ): Http[OptionT[G, *], F] =
      new EndpointToHttp4sServer(serverOptions).toHttp(t, se)
  }

  implicit class RichHttp4sServerEndpoint[I, E, O, F[_]](se: ServerEndpoint[I, E, O, Fs2Streams[F] with WebSockets, F]) {
    def toRoutes(implicit serverOptions: Http4sServerOptions[F], fs: Concurrent[F], fcs: ContextShift[F]): HttpRoutes[F] =
      new EndpointToHttp4sServer(serverOptions).toRoutes(se)
  }

  implicit class RichHttp4sServerEndpoints0[F[_], G[_]](serverEndpoints: List[ServerEndpoint[_, _, _, Fs2Streams[F] with WebSockets, G]]) {
    def toHttp(t: F ~> G)(implicit
        serverOptions: Http4sServerOptions[F],
        gs: Sync[G],
        fs: Concurrent[F],
        fcs: ContextShift[F]
    ): Http[OptionT[G, *], F] = {
      new EndpointToHttp4sServer[F](serverOptions).toHttp(t)(serverEndpoints)
    }
  }

  implicit class RichHttp4sServerEndpoints[F[_]](serverEndpoints: List[ServerEndpoint[_, _, _, Fs2Streams[F] with WebSockets, F]]) {
    def toRoutes(implicit serverOptions: Http4sServerOptions[F], fs: Concurrent[F], fcs: ContextShift[F]): HttpRoutes[F] = {
      new EndpointToHttp4sServer(serverOptions).toRoutes(serverEndpoints)
    }
  }
}
