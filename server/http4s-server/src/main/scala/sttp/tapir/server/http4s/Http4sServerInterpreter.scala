package sttp.tapir.server.http4s

import cats.arrow.FunctionK
import cats.data.{Kleisli, OptionT}
import cats.effect.{Concurrent, ContextShift, Sync, Timer}
import cats.implicits._
import cats.~>
import fs2.Pipe
import fs2.concurrent.Queue
import org.http4s._
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.util.CaseInsensitiveString
import org.http4s.websocket.WebSocketFrame
import org.log4s.{Logger, getLogger}
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint

import scala.reflect.ClassTag

trait Http4sServerInterpreter extends Http4sServerToHttpInterpreter[F, F] {
  def toRoutes[I, E, O](e: Endpoint[I, E, O, Fs2Streams[F] with WebSockets])(
      logic: I => F[Either[E, O]]
  ): HttpRoutes[F] = toRoutes(
    e.serverLogic(logic)
  )

  def toRouteRecoverErrors[I, E, O](e: Endpoint[I, E, O, Fs2Streams[F] with WebSockets])(logic: I => F[O])(implicit
      eIsThrowable: E <:< Throwable,
      eClassTag: ClassTag[E]
  ): HttpRoutes[F] = toRoutes(e.serverLogicRecoverErrors(logic))

  def toRoutes[I, E, O](se: ServerEndpoint[I, E, O, Fs2Streams[F] with WebSockets, F]): HttpRoutes[F] = toRoutes(List(se))

  def toRoutes(serverEndpoints: List[ServerEndpoint[_, _, _, Fs2Streams[F] with WebSockets, F]]): HttpRoutes[F] = {
    toHttp(serverEndpoints)(fToG)(gToF)
  }
}

object Http4sServerInterpreter {
  def apply[F[_]]()(implicit _fa: Async[F]): Http4sServerInterpreter[F] = {
    new Http4sServerInterpreter[F] {
      override implicit def ga: Async[G] = _fa
      override implicit def fa: Async[F] = _fa
      override def fToG: F ~> F = FunctionK.id[F]
      override def gToF: F ~> F = FunctionK.id[F]
    }
  }

  def apply[F[_]](
      serverOptions: Http4sServerOptions[F, F]
  )(implicit _fa: Async[F]): Http4sServerInterpreter[F] = {
    new Http4sServerInterpreter[F] {
      override implicit def ga: Async[G] = _fa
      override implicit def fa: Async[F] = _fa
      override def fToG: F ~> F = FunctionK.id[F]
      override def gToF: F ~> F = FunctionK.id[F]
      override def fToG: F ~> F = FunctionK.id[F]
      override def gToF: F ~> F = FunctionK.id[F]
      override def http4sServerOptions: Http4sServerOptions[F, F] = serverOptions
    }
  }
}
