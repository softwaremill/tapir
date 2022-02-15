package sttp.tapir.server.armeria.cats

import cats.data.NonEmptyList
import cats.effect.std.Dispatcher
import cats.effect.{IO, Resource}
import com.linecorp.armeria.server.{HttpServiceWithRoutes, Server}
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests.Port

class ArmeriaCatsTestServerInterpreter(dispatcher: Dispatcher[IO])
    extends TestServerInterpreter[IO, Fs2Streams[IO], HttpServiceWithRoutes] {

  override def route(
      e: ServerEndpoint[Fs2Streams[IO], IO],
      decodeFailureHandler: Option[DecodeFailureHandler],
      metricsInterceptor: Option[MetricsRequestInterceptor[IO]] = None
  ): HttpServiceWithRoutes = {
    val options: ArmeriaCatsServerOptions[IO] = {
      ArmeriaCatsServerOptions
        .customInterceptors[IO](dispatcher)
        .metricsInterceptor(metricsInterceptor)
        .decodeFailureHandler(decodeFailureHandler.getOrElse(DefaultDecodeFailureHandler.default))
        .options
    }
    ArmeriaCatsServerInterpreter(options).toService(e)
  }

  override def route(es: List[ServerEndpoint[Fs2Streams[IO], IO]]): HttpServiceWithRoutes = {
    ArmeriaCatsServerInterpreter(dispatcher).toService(es)
  }

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
