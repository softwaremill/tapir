package sttp.tapir.server.vertx

import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.web.RoutingContext
import sttp.tapir.model.SttpFile
import sttp.tapir.server.interceptor.Interceptor
import sttp.tapir.server.interceptor.content.UnsupportedMediaTypeInterceptor
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DecodeFailureInterceptor, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.exception.{DefaultExceptionHandler, ExceptionHandler, ExceptionInterceptor}
import sttp.tapir.server.interceptor.log.{ServerLog, ServerLogInterceptor}
import zio.{RIO, Task, ZIO}

import java.io.File

final case class VertxZioServerOptions[F[_]](
    uploadDirectory: SttpFile,
    deleteFiles: Seq[SttpFile] => F[Unit],
    maxQueueSizeForReadStream: Int,
    interceptors: List[Interceptor[F, RoutingContext => Unit]]
) extends VertxServerOptions[F] {
  def prependInterceptor(i: Interceptor[F, RoutingContext => Unit]): VertxZioServerOptions[F] =
    copy(interceptors = i :: interceptors)
  def appendInterceptor(i: Interceptor[F, RoutingContext => Unit]): VertxZioServerOptions[F] =
    copy(interceptors = interceptors :+ i)
}

object VertxZioServerOptions {

  /** Creates default [[VertxZioServerOptions]] with custom interceptors, sitting between two interceptor groups:
    * 1. the optional exception interceptor and the optional logging interceptor (which should typically be first
    *    when processing the request, and last when processing the response)),
    * 2. the optional unsupported media type interceptor and the decode failure handling interceptor (which should
    *    typically be last when processing the request).
    *
    * The options can be then further customised using copy constructors or the methods to append/prepend
    * interceptors.
    *
    * @param exceptionHandler Whether to respond to exceptions, or propagate them to vertx.
    * @param serverLog The server log using which an interceptor will be created, if any. To keep the default, use
    *                  `VertxEndpointOptions.defaultServerLog`
    * @param additionalInterceptors Additional interceptors, e.g. handling decode failures, or providing alternate
    *                               responses.
    * @param unsupportedMediaTypeInterceptor Whether to return 415 (unsupported media type) if there's no body in the
    *                                        endpoint's outputs, which can satisfy the constraints from the `Accept`
    *                                        header.
    * @param decodeFailureHandler The decode failure handler, from which an interceptor will be created.
    */
  def customInterceptors[R](
      exceptionHandler: Option[ExceptionHandler] = Some(DefaultExceptionHandler),
      serverLog: Option[ServerLog[Unit]] = Some(VertxServerOptions.defaultServerLog(LoggerFactory.getLogger("tapir-vertx"))),
      additionalInterceptors: List[Interceptor[RIO[R, *], RoutingContext => Unit]] = Nil,
      unsupportedMediaTypeInterceptor: Option[UnsupportedMediaTypeInterceptor[RIO[R, *], RoutingContext => Unit]] = Some(
        new UnsupportedMediaTypeInterceptor[RIO[R, *], RoutingContext => Unit]()
      ),
      decodeFailureHandler: DecodeFailureHandler = DefaultDecodeFailureHandler.handler
  ): VertxZioServerOptions[RIO[R, *]] = {
    VertxZioServerOptions(
      SttpFile.fromFile(File.createTempFile("tapir", null).getParentFile.getAbsoluteFile),
      files => Task[Unit](files.foreach(_.toFile.delete())),
      maxQueueSizeForReadStream = 16,
      exceptionHandler.map(new ExceptionInterceptor[RIO[R, *], RoutingContext => Unit](_)).toList ++
        serverLog.map(new ServerLogInterceptor[Unit, RIO[R, *], RoutingContext => Unit](_, (_, _) => RIO.unit)).toList ++
        additionalInterceptors ++
        unsupportedMediaTypeInterceptor.toList ++
        List(new DecodeFailureInterceptor[RIO[R, *], RoutingContext => Unit](decodeFailureHandler))
    )
  }

  implicit def default[R]: VertxZioServerOptions[RIO[R, *]] = customInterceptors()
}
