package sttp.tapir.server.ziohttp

import cats.data.NonEmptyList
import cats.effect.{Async, IO, Resource}
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests.Port
import zhttp.http._
import zhttp.service.server.ServerChannelFactory
import zhttp.service.{EventLoopGroup, Server}
import zio._
import zio.blocking.Blocking
import zio.interop.ZManagedSyntax
import zio.interop.catz.taskEffectInstance
import zio.stream.ZStream

import scala.reflect.ClassTag

class ZioHttpTestServerInterpreter
  extends TestServerInterpreter[RIO[Blocking, *], ZioStreams, Http[Blocking, Throwable, Request, Response[Blocking, Throwable]], ZStream[Blocking, Throwable, Byte]] {

  override def route[I, E, O](
      e: ServerEndpoint[I, E, O, ZioStreams, RIO[Blocking, *]],
      decodeFailureHandler: Option[DecodeFailureHandler],
      metricsInterceptor: Option[MetricsRequestInterceptor[RIO[Blocking, *], ZStream[Blocking, Throwable, Byte]]]
  ): Http[Blocking, Throwable, Request, Response[Blocking, Throwable]] = {
    val serverOptions: ZioHttpServerOptions[Blocking] = ZioHttpServerOptions.customInterceptors(
      metricsInterceptor = metricsInterceptor,
      decodeFailureHandler = decodeFailureHandler.getOrElse(DefaultDecodeFailureHandler.handler)
    )
    ZioHttpInterpreter(serverOptions).toRoutes(e)
  }

  override def routeRecoverErrors[I, E <: Throwable, O](e: Endpoint[I, E, O, ZioStreams], fn: I => RIO[Blocking, O])(implicit
      eClassTag: ClassTag[E]
  ): Http[Blocking, Throwable, Request, Response[Blocking, Throwable]] = {
    ZioHttpInterpreter().toRouteRecoverErrors(e)(fn)
  }

  override def server(routes: NonEmptyList[Http[Blocking, Throwable, Request, Response[Blocking, Throwable]]]): Resource[IO, Port] = {
    val as: Async[IO] = Async[IO]
    implicit val r: Runtime[Blocking] = Runtime.default
    val zioHttpServerPort = 8090
    val server: Server[Blocking, Throwable] =
      Server.port(zioHttpServerPort) ++ Server.app(routes.toList.reduce(_ <> _)) ++ Server.maxRequestSize(1000000)
    val managedServer: ZManaged[Blocking, Nothing, Exit[Throwable, Unit]] = Server
      .make(server)
      .run
      .provideSomeLayer[Blocking](EventLoopGroup.auto(0) ++ ServerChannelFactory.auto)

    new ZManagedSyntax(managedServer)
      .toResource(as, taskEffectInstance(r))
      .map(_ => zioHttpServerPort)
  }
}
