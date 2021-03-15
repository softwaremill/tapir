package sttp.tapir.server.vertx

import cats.Applicative
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.web.RoutingContext
import sttp.tapir.server.interceptor.EndpointInterceptor
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DecodeFailureInterceptor, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.log.{ServerLog, ServerLogInterceptor}

import java.io.File

final case class VertxCatsEndpointOptions[F[_]](
    uploadDirectory: File,
    maxQueueSizeForReadStream: Int,
    interceptors: List[EndpointInterceptor[F, RoutingContext => Unit]]
) extends VertxEndpointOptions[F] {
  def prependInterceptor(i: EndpointInterceptor[F, RoutingContext => Unit]): VertxCatsEndpointOptions[F] =
    copy(interceptors = i :: interceptors)
  def appendInterceptor(i: EndpointInterceptor[F, RoutingContext => Unit]): VertxCatsEndpointOptions[F] =
    copy(interceptors = interceptors :+ i)
}

object VertxCatsEndpointOptions {

  /** Creates default [[VertxCatsEndpointOptions]] with custom interceptors, sitting between an optional logging
    * interceptor, and the ultimate decode failure handling interceptor.
    *
    * The options can be then further customised using copy constructors or the methods to append/prepend
    * interceptors.
    *
    * @param serverLog The server log using which an interceptor will be created, if any. To keep the default, use
    *                  `VertxEndpointOptions.defaultServerLog`
    * @param additionalInterceptors Additional interceptors, e.g. handling decode failures, or providing alternate
    *                               responses.
    * @param decodeFailureHandler The decode failure handler, from which an interceptor will be created.
    */
  def customInterceptors[F[_]: Applicative](
      serverLog: Option[ServerLog[Unit]] = Some(VertxEndpointOptions.defaultServerLog(LoggerFactory.getLogger("tapir-vertx"))),
      additionalInterceptors: List[EndpointInterceptor[F, RoutingContext => Unit]] = Nil,
      decodeFailureHandler: DecodeFailureHandler = DefaultDecodeFailureHandler.handler
  ): VertxCatsEndpointOptions[F] = {
    VertxCatsEndpointOptions(
      File.createTempFile("tapir", null).getParentFile.getAbsoluteFile,
      maxQueueSizeForReadStream = 16,
      serverLog.map(new ServerLogInterceptor[Unit, F, RoutingContext => Unit](_, (_, _) => Applicative[F].unit)).toList ++
        additionalInterceptors ++
        List(new DecodeFailureInterceptor[F, RoutingContext => Unit](decodeFailureHandler))
    )
  }

  implicit def default[F[_]: Applicative]: VertxCatsEndpointOptions[F] = customInterceptors()
}
