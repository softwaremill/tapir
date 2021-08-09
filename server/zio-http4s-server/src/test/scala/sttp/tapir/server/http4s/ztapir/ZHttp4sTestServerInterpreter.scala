package sttp.tapir.server.http4s.ztapir

import cats.data.{Kleisli, NonEmptyList}
import cats.effect.{IO, Resource}
import cats.syntax.all._
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.syntax.kleisli._
import org.http4s.{HttpRoutes, Request, Response}
import sttp.capabilities.WebSockets
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.server.http4s.{Http4sResponseBody, Http4sServerOptions}
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests.Port
import sttp.tapir.ztapir.{ZEndpoint, ZServerEndpoint}
import zio.RIO
import zio.blocking.Blocking
import zio.clock.Clock
import zio.interop.catz._
import zio.interop.catz.implicits._

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

class ZHttp4sTestServerInterpreter
    extends TestServerInterpreter[RIO[Clock with Blocking, *], ZioStreams with WebSockets, HttpRoutes[
      RIO[Clock with Blocking, *]
    ]] {
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  override def route[I, E, O](
      e: ZServerEndpoint[Clock with Blocking, I, E, O],
      decodeFailureHandler: Option[DecodeFailureHandler] = None,
      metricsInterceptor: Option[MetricsRequestInterceptor[RIO[Clock with Blocking, *]]] = None
  ): HttpRoutes[RIO[Clock with Blocking, *]] = {
    val serverOptions: Http4sServerOptions[RIO[Clock with Blocking, *], RIO[Clock with Blocking, *]] = Http4sServerOptions
      .customInterceptors[RIO[Clock with Blocking, *], RIO[Clock with Blocking, *]]
      .metricsInterceptor(metricsInterceptor)
      .decodeFailureHandler(decodeFailureHandler.getOrElse(DefaultDecodeFailureHandler.handler))
      .options

    ZHttp4sServerInterpreter(serverOptions).from(e).toRoutes
  }

  override def route[I, E, O](es: List[ZServerEndpoint[Clock with Blocking, I, E, O]]): HttpRoutes[RIO[Clock with Blocking, *]] = {
    ZHttp4sServerInterpreter().from(es).toRoutes
  }

  override def routeRecoverErrors[I, E <: Throwable, O](e: ZEndpoint[I, E, O], fn: I => RIO[Clock with Blocking, O])(implicit
      eClassTag: ClassTag[E]
  ): HttpRoutes[RIO[Clock with Blocking, *]] = {
    ZHttp4sServerInterpreter().from(e.serverLogicRecoverErrors(fn)).toRoutes
  }

  override def server(routes: NonEmptyList[HttpRoutes[RIO[Clock with Blocking, *]]]): Resource[IO, Port] = {
    val service: Kleisli[RIO[Clock with Blocking, *], Request[RIO[Clock with Blocking, *]], Response[RIO[Clock with Blocking, *]]] =
      routes.reduceK.orNotFound

    val serverResource = BlazeServerBuilder[RIO[Clock with Blocking, *]](ExecutionContext.global)
      .bindHttp(0, "localhost")
      .withHttpApp(service)
      .resource
      .map(_.address.getPort)

    // Converting a zio.RIO-resource to an cats.IO-resource
    val runtime = implicitly[zio.Runtime[Clock with Blocking]]
    Resource
      .eval(IO.fromFuture(IO(runtime.unsafeRunToFuture(serverResource.allocated))))
      .flatMap { case (port, release) =>
        Resource.make(IO.pure(port))(_ => IO.fromFuture(IO(runtime.unsafeRunToFuture(release))))
      }
  }
}
