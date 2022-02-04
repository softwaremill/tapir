package sttp.tapir.server.armeria.zio

import _root_.zio.{Runtime, Task}
import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import com.linecorp.armeria.common.logging.LogLevel
import com.linecorp.armeria.server.Server
import com.linecorp.armeria.server.logging.{AccessLogWriter, LoggingService}
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.armeria.TapirService
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests.Port

class ArmeriaZioTestServerInterpreter extends TestServerInterpreter[Task, ZioStreams, TapirService[ZioStreams, Task]] {
  import ArmeriaZioTestServerInterpreter._
  override def route(
      e: ServerEndpoint[ZioStreams, Task],
      decodeFailureHandler: Option[DecodeFailureHandler],
      metricsInterceptor: Option[MetricsRequestInterceptor[Task]] = None
  ): TapirService[ZioStreams, Task] = {
    val options: ArmeriaZioServerOptions[Task] = {
      ArmeriaZioServerOptions.customInterceptors
        .metricsInterceptor(metricsInterceptor)
        .decodeFailureHandler(decodeFailureHandler.getOrElse(DefaultDecodeFailureHandler.default))
        .options
    }
    ArmeriaZioServerInterpreter(options).toRoute(e)
  }

  override def route(es: List[ServerEndpoint[ZioStreams, Task]]): TapirService[ZioStreams, Task] = {
    ArmeriaZioServerInterpreter[Any]().toRoute(es)
  }

  override def server(routes: NonEmptyList[TapirService[ZioStreams, Task]]): Resource[IO, Port] = {
    val bind = IO.fromCompletableFuture(
      IO {
        val serverBuilder = Server
          .builder()
          .maxRequestLength(0)
        routes.foldLeft(serverBuilder)((sb, route) => sb.service(route))
        val server = serverBuilder.build()
        server.start().thenApply(_ => server)
      }
    )
    Resource.make(bind)(binding => IO.fromCompletableFuture(IO(binding.stop())).void).map(_.activeLocalPort())
  }
}

object ArmeriaZioTestServerInterpreter {
  implicit val runtime: Runtime[zio.ZEnv] = Runtime.default
}
