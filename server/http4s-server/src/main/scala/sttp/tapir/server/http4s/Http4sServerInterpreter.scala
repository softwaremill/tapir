package sttp.tapir.server.http4s

import cats.arrow.FunctionK
import cats.effect.{Async, Sync}
import cats.~>
import org.http4s._
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint

import scala.reflect.ClassTag

trait Http4sServerInterpreter[F[_]] extends Http4sServerToHttpInterpreter[F, F] {
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
      override implicit def gs: Sync[F] = _fa
      override implicit def fa: Async[F] = _fa
      override def fToG: F ~> F = FunctionK.id[F]
      override def gToF: F ~> F = FunctionK.id[F]
    }
  }

  def apply[F[_]](
      serverOptions: Http4sServerOptions[F, F]
  )(implicit _fa: Async[F]): Http4sServerInterpreter[F] = {
    new Http4sServerInterpreter[F] {
      override implicit def gs: Sync[F] = _fa
      override implicit def fa: Async[F] = _fa
      override def fToG: F ~> F = FunctionK.id[F]
      override def gToF: F ~> F = FunctionK.id[F]
      override def http4sServerOptions: Http4sServerOptions[F, F] = serverOptions
    }
  }
}
