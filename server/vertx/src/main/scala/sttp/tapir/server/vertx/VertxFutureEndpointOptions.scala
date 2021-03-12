package sttp.tapir.server.vertx

import io.vertx.core.{Context, Vertx}
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.web.RoutingContext
import sttp.tapir.server.interceptor.EndpointInterceptor
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DecodeFailureInterceptor, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.log.{ServerLog, ServerLogInterceptor}

import java.io.File
import scala.concurrent.{ExecutionContext, Future}

final case class VertxFutureEndpointOptions(
    uploadDirectory: File,
    interceptors: List[EndpointInterceptor[Future, RoutingContext => Unit]],
    private val specificExecutionContext: Option[ExecutionContext]
) extends VertxEndpointOptions[Future] {
  def executionContextOr(default: ExecutionContext): ExecutionContext =
    specificExecutionContext.getOrElse(default)

  private[vertx] def executionContextOrCurrentCtx(rc: RoutingContext) =
    executionContextOr(new VertxExecutionContext(rc.vertx, rc.vertx.getOrCreateContext))

  def prependInterceptor(i: EndpointInterceptor[Future, RoutingContext => Unit]): VertxFutureEndpointOptions =
    copy(interceptors = i :: interceptors)
  def appendInterceptor(i: EndpointInterceptor[Future, RoutingContext => Unit]): VertxFutureEndpointOptions =
    copy(interceptors = interceptors :+ i)
}

object VertxFutureEndpointOptions {

  /** Creates default [[VertxFutureEndpointOptions]] with custom interceptors, sitting between an optional logging
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
  def customInterceptors(
      serverLog: Option[ServerLog[Unit]] = Some(VertxEndpointOptions.defaultServerLog(LoggerFactory.getLogger("tapir-vertx"))),
      additionalInterceptors: List[EndpointInterceptor[Future, RoutingContext => Unit]] = Nil,
      decodeFailureHandler: DecodeFailureHandler = DefaultDecodeFailureHandler.handler
  ): VertxFutureEndpointOptions = {
    VertxFutureEndpointOptions(
      File.createTempFile("tapir", null).getParentFile.getAbsoluteFile,
      serverLog.map(new ServerLogInterceptor[Unit, Future, RoutingContext => Unit](_, (_, _) => Future.successful(()))).toList ++
        additionalInterceptors ++
        List(new DecodeFailureInterceptor[Future, RoutingContext => Unit](decodeFailureHandler)),
      None
    )
  }

  implicit val default: VertxFutureEndpointOptions = customInterceptors()
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
