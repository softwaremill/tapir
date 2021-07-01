package sttp.tapir.server.ziohttp

import cats.data.NonEmptyList
import cats.effect.{Async, IO, Resource}
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.server.zhttp.{ZHttpInterpreter, ZHttpServerOptions}
import sttp.tapir.tests.Port
import zhttp.http._
import zhttp.service.server.ServerChannelFactory
import zhttp.service.{EventLoopGroup, Server, ServerChannelFactory}
import zio._
import zio.blocking.Blocking
import zio.console.Console
import zio.interop.ZManagedSyntax
import zio.interop.catz.taskEffectInstance

import scala.reflect.ClassTag

class ZHttpTestServerInterpreter
  extends TestServerInterpreter[RIO[Blocking, *], ZioStreams, Http[Blocking, Throwable, Request, Response[Blocking, Throwable]], ZioStreams] {

  override def route[I, E, O](
      e: ServerEndpoint[I, E, O, ZioStreams, RIO[Blocking, *]],
      decodeFailureHandler: Option[DecodeFailureHandler],
      metricsInterceptor: Option[MetricsRequestInterceptor[RIO[Blocking, *], ZioStreams]]
  ): Http[Blocking, Throwable, Request, Response[Blocking, Throwable]] = {
    val serverOptions: ZHttpServerOptions[Blocking] = ZHttpServerOptions.customInterceptors(
      decodeFailureHandler = decodeFailureHandler.getOrElse(DefaultDecodeFailureHandler.handler)
    )
    ZHttpInterpreter(serverOptions).toRoutes(e)
  }

  override def routeRecoverErrors[I, E <: Throwable, O](e: Endpoint[I, E, O, ZioStreams], fn: I => RIO[Blocking, O])(implicit
      eClassTag: ClassTag[E]
  ): Http[Blocking, Throwable, Request, Response[Blocking, Throwable]] = {
    ZHttpInterpreter().toRouteRecoverErrors(e)(fn)
  }

  override def server(routes: NonEmptyList[Http[Blocking, Throwable, Request, Response[Blocking, Throwable]]]): Resource[IO, Port] = {
    val as: Async[IO] = Async[IO]
    implicit val r: Runtime[Blocking] = Runtime.default
    val zioHttpServerPort = 8091
    val server: Server[Blocking, Throwable] = Server.port(zioHttpServerPort) ++ Server.app(routes.toList.reduce(_ <> _))
    val managedServer: ZManaged[Blocking, Nothing, Exit[Throwable, Unit]] = Server
      .make(server)
      .run
      .provideSomeLayer[Blocking](EventLoopGroup.auto(0) ++ ServerChannelFactory.auto)

    new ZManagedSyntax(managedServer)
      .toResource(as, taskEffectInstance(r))
      .map(_ => zioHttpServerPort)
  }
}
