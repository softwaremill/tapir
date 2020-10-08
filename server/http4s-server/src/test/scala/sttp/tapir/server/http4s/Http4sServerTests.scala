package sttp.tapir.server.http4s

import cats.data.{Kleisli, NonEmptyList}
import cats.effect._
import cats.syntax.all._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.syntax.kleisli._
import org.http4s.{HttpRoutes, Request, Response}
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.Endpoint
import sttp.tapir.server.tests.ServerTests
import sttp.tapir.server.{DecodeFailureHandler, ServerDefaults, ServerEndpoint}
import sttp.tapir.tests.Port

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

abstract class Http4sServerTests[R >: Fs2Streams[IO] with WebSockets] extends ServerTests[IO, R, HttpRoutes[IO]] {
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit val contextShift: ContextShift[IO] = IO.contextShift(ec)
  implicit val timer: Timer[IO] = IO.timer(ec)

  override def pureResult[T](t: T): IO[T] = IO.pure(t)
  override def suspendResult[T](t: => T): IO[T] = IO.apply(t)

  override def route[I, E, O](
      e: ServerEndpoint[I, E, O, R, IO],
      decodeFailureHandler: Option[DecodeFailureHandler] = None
  ): HttpRoutes[IO] = {
    implicit val serverOptions: Http4sServerOptions[IO] = Http4sServerOptions
      .default[IO]
      .copy(
        decodeFailureHandler = decodeFailureHandler.getOrElse(ServerDefaults.decodeFailureHandler)
      )
    e.toRoutes
  }

  override def routeRecoverErrors[I, E <: Throwable, O](e: Endpoint[I, E, O, R], fn: I => IO[O])(implicit
      eClassTag: ClassTag[E]
  ): HttpRoutes[IO] = {
    e.toRouteRecoverErrors(fn)
  }

  override def server(routes: NonEmptyList[HttpRoutes[IO]], port: Port): Resource[IO, Unit] = {
    val service: Kleisli[IO, Request[IO], Response[IO]] = routes.reduceK.orNotFound

    BlazeServerBuilder[IO](ExecutionContext.global)
      .bindHttp(port, "localhost")
      .withHttpApp(service)
      .resource
      .void
  }
}
