package sttp.tapir.server.vertx

import io.vertx.core.logging.LoggerFactory
import io.vertx.core.{Context, Vertx}
import io.vertx.ext.web.RoutingContext
import sttp.tapir.server.interceptor.Interceptor
import sttp.tapir.server.interceptor.content.UnsupportedMediaTypeInterceptor
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DecodeFailureInterceptor, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.exception.{DefaultExceptionHandler, ExceptionHandler, ExceptionInterceptor}
import sttp.tapir.server.interceptor.log.{ServerLog, ServerLogInterceptor}
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor

import java.io.File
import scala.concurrent.{ExecutionContext, Future}

final case class VertxFutureServerOptions(
    uploadDirectory: File,
    interceptors: List[Interceptor[Future, RoutingContext => Unit]],
    private val specificExecutionContext: Option[ExecutionContext]
) extends VertxServerOptions[Future] {
  def executionContextOr(default: ExecutionContext): ExecutionContext =
    specificExecutionContext.getOrElse(default)

  private[vertx] def executionContextOrCurrentCtx(rc: RoutingContext) =
    executionContextOr(new VertxExecutionContext(rc.vertx, rc.vertx.getOrCreateContext))

  def prependInterceptor(i: Interceptor[Future, RoutingContext => Unit]): VertxFutureServerOptions =
    copy(interceptors = i :: interceptors)
  def appendInterceptor(i: Interceptor[Future, RoutingContext => Unit]): VertxFutureServerOptions =
    copy(interceptors = interceptors :+ i)
}

object VertxFutureServerOptions {

  /** Creates default [[VertxFutureServerOptions]] with custom interceptors, sitting between two interceptor groups:
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
  def customInterceptors(
      metricsInterceptor: Option[MetricsRequestInterceptor[Future, RoutingContext => Unit]] = None,
      exceptionHandler: Option[ExceptionHandler] = Some(DefaultExceptionHandler),
      serverLog: Option[ServerLog[Unit]] = Some(VertxServerOptions.defaultServerLog(LoggerFactory.getLogger("tapir-vertx"))),
      additionalInterceptors: List[Interceptor[Future, RoutingContext => Unit]] = Nil,
      unsupportedMediaTypeInterceptor: Option[UnsupportedMediaTypeInterceptor[Future, RoutingContext => Unit]] = Some(
        new UnsupportedMediaTypeInterceptor()
      ),
      decodeFailureHandler: DecodeFailureHandler = DefaultDecodeFailureHandler.handler
  ): VertxFutureServerOptions = {
    VertxFutureServerOptions(
      File.createTempFile("tapir", null).getParentFile.getAbsoluteFile,
      metricsInterceptor.toList ++
        exceptionHandler.map(new ExceptionInterceptor[Future, RoutingContext => Unit](_)).toList ++
        serverLog.map(new ServerLogInterceptor[Unit, Future, RoutingContext => Unit](_, (_, _) => Future.successful(()))).toList ++
        additionalInterceptors ++
        unsupportedMediaTypeInterceptor.toList ++
        List(new DecodeFailureInterceptor[Future, RoutingContext => Unit](decodeFailureHandler)),
      None
    )
  }

  implicit val default: VertxFutureServerOptions = customInterceptors()
}

class VertxExecutionContext(val vertx: Vertx, val ctx: Context) extends ExecutionContext {
  override def execute(runnable: Runnable): Unit =
    if (vertx.getOrCreateContext() != ctx) {
      ctx.runOnContext((_: Void) => runnable.run())
    } else {
      runnable.run()
    }

  override def reportFailure(cause: Throwable): Unit =
    VertxExecutionContext.Log.error("Failed executing", cause)
}

object VertxExecutionContext {
  private[vertx] val Log = LoggerFactory.getLogger(classOf[VertxExecutionContext].getName)
}
