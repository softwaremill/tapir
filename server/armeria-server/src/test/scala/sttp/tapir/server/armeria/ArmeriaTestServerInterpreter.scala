package sttp.tapir.server.armeria

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import com.linecorp.armeria.server.{HttpServiceWithRoutes, Server}
import scala.concurrent.Future
import sttp.capabilities.armeria.ArmeriaStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests.Port

class ArmeriaTestServerInterpreter() extends TestServerInterpreter[Future, ArmeriaStreams, HttpServiceWithRoutes] {

  override def route(
      e: ServerEndpoint[ArmeriaStreams, Future],
      decodeFailureHandler: Option[DecodeFailureHandler] = None,
      metricsInterceptor: Option[MetricsRequestInterceptor[Future]] = None
  ): HttpServiceWithRoutes = {
    val serverOptions: ArmeriaFutureServerOptions = ArmeriaFutureServerOptions.customInterceptors
      .metricsInterceptor(metricsInterceptor)
      .decodeFailureHandler(decodeFailureHandler.getOrElse(DefaultDecodeFailureHandler.default))
      .options
    ArmeriaFutureServerInterpreter(serverOptions).toRoute(e)
  }

  override def route(es: List[ServerEndpoint[ArmeriaStreams, Future]]): HttpServiceWithRoutes =
    ArmeriaFutureServerInterpreter().toRoute(es)

  override def server(routes: NonEmptyList[HttpServiceWithRoutes]): Resource[IO, Port] = {
    val bind = IO.fromCompletableFuture(
      IO {
        val serverBuilder = Server
          .builder()
          .maxRequestLength(0)
        routes.foldLeft(serverBuilder)((sb, route) => sb.service(route))
        val server = serverBuilder.build()
        server.start().thenApply[Server](_ => server)
      }
    )
    Resource.make(bind)(binding => IO.fromCompletableFuture(IO(binding.stop())).void).map(_.activeLocalPort())
  }
}
