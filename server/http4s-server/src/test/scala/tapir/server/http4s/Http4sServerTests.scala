package tapir.server.http4s

import cats.data.{Kleisli, NonEmptyList}
import cats.effect._
import cats.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.syntax.kleisli._
import org.http4s.{EntityBody, HttpRoutes, Request, Response}
import tapir.server.StatusMapper
import tapir.server.tests.ServerTests
import tapir.typelevel.ParamsAsArgs
import tapir.Endpoint

import scala.concurrent.ExecutionContext

class Http4sServerTests extends ServerTests[IO, EntityBody[IO], HttpRoutes[IO]] {

  implicit private val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit private val contextShift: ContextShift[IO] = IO.contextShift(ec)
  implicit private val timer: Timer[IO] = IO.timer(ec)

  override def pureResult[T](t: T): IO[T] = IO.pure(t)

  override def route[I, E, O, FN[_]](e: Endpoint[I, E, O, EntityBody[IO]],
                                     fn: FN[IO[Either[E, O]]],
                                     statusMapper: StatusMapper[O],
                                     errorStatusMapper: StatusMapper[E])(implicit paramsAsArgs: ParamsAsArgs.Aux[I, FN]): HttpRoutes[IO] = {

    implicit val sm: StatusMapper[O] = statusMapper
    implicit val esm: StatusMapper[E] = errorStatusMapper
    e.toRoutes(fn)
  }

  override def server(routes: NonEmptyList[HttpRoutes[IO]], port: Port): Resource[IO, Unit] = {

    val service: Kleisli[IO, Request[IO], Response[IO]] = routes.reduceK.orNotFound

    BlazeServerBuilder[IO]
      .bindHttp(port, "localhost")
      .withHttpApp(service)
      .resource
      .map(_ => ())
  }
}
