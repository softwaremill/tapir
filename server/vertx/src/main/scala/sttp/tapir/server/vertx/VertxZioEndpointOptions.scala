package sttp.tapir.server.vertx

import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.web.RoutingContext
import sttp.tapir.server.interceptor.EndpointInterceptor
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DecodeFailureInterceptor, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.log.{ServerLog, ServerLogInterceptor}
import zio.RIO

import java.io.File

final case class VertxZioEndpointOptions[F[_]](
    uploadDirectory: File,
    maxQueueSizeForReadStream: Int,
    interceptors: List[EndpointInterceptor[F, RoutingContext => Unit]]
) extends VertxEndpointOptions[F] {
  def prependInterceptor(i: EndpointInterceptor[F, RoutingContext => Unit]): VertxZioEndpointOptions[F] =
    copy(interceptors = i :: interceptors)
  def appendInterceptor(i: EndpointInterceptor[F, RoutingContext => Unit]): VertxZioEndpointOptions[F] =
    copy(interceptors = interceptors :+ i)
}

object VertxZioEndpointOptions {

  /** Creates default [[VertxZioEndpointOptions]] with custom interceptors, sitting between an optional logging
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
  def customInterceptors[R](
      serverLog: Option[ServerLog[Unit]] = Some(VertxEndpointOptions.defaultServerLog(LoggerFactory.getLogger("tapir-vertx"))),
      additionalInterceptors: List[EndpointInterceptor[RIO[R, *], RoutingContext => Unit]] = Nil,
      decodeFailureHandler: DecodeFailureHandler = DefaultDecodeFailureHandler.handler
  ): VertxZioEndpointOptions[RIO[R, *]] = {
    VertxZioEndpointOptions(
      File.createTempFile("tapir", null).getParentFile.getAbsoluteFile,
      maxQueueSizeForReadStream = 16,
      serverLog.map(new ServerLogInterceptor[Unit, RIO[R, *], RoutingContext => Unit](_, (_, _) => RIO.unit)).toList ++
        additionalInterceptors ++
        List(new DecodeFailureInterceptor[RIO[R, *], RoutingContext => Unit](decodeFailureHandler))
    )
  }

  implicit def default[R]: VertxZioEndpointOptions[RIO[R, *]] = customInterceptors()
}
