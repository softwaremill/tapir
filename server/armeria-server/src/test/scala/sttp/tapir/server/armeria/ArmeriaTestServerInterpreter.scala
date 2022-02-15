package sttp.tapir.server.armeria

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import com.linecorp.armeria.common.HttpStatus
import com.linecorp.armeria.common.logging.LogLevel
import com.linecorp.armeria.server.logging.LoggingService
import com.linecorp.armeria.server.{HttpServiceWithRoutes, HttpStatusException, Server}
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
    ArmeriaFutureServerInterpreter(serverOptions).toService(e)
  }

  override def route(es: List[ServerEndpoint[ArmeriaStreams, Future]]): HttpServiceWithRoutes =
    ArmeriaFutureServerInterpreter().toService(es)

  override def server(routes: NonEmptyList[HttpServiceWithRoutes]): Resource[IO, Port] = {
    val bind = IO.fromCompletableFuture(
      IO {
        val serverBuilder = Server
          .builder()
//          .decorator((delegate, ctx, req) => {
//            try delegate.serve(ctx, req)
//            catch {
//              case ex: HttpStatusException =>
//                // Armeria returns "405 Method Not Allowed" if unsupported HTTP method is received.
//                // Overrides HTTP status at Armeria's decorator level to pass 'ServerRejectTests' that expects
//                // "404 Not found" for unsupported HTTP methods.
//                if (ex.httpStatus == HttpStatus.METHOD_NOT_ALLOWED)
//                  throw HttpStatusException.of(HttpStatus.NOT_FOUND)
//                else throw ex
//            }
//          })
          .maxRequestLength(0)
        routes.foldLeft(serverBuilder)((sb, route) => sb.service(route))
        val server = serverBuilder.build()
        server.start().thenApply[Server](_ => server)
      }
    )
    Resource.make(bind)(binding => IO.fromCompletableFuture(IO(binding.stop())).void).map(_.activeLocalPort())
  }
}
