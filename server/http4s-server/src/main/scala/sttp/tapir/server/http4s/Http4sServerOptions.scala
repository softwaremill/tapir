package sttp.tapir.server.http4s

import cats.Applicative
import cats.effect.Sync
import sttp.tapir.Defaults
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.log.{DefaultServerLog, ServerLog, ServerLogInterceptor}
import sttp.tapir.server.interceptor.Interceptor
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DecodeFailureInterceptor, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.exception.{DefaultExceptionHandler, ExceptionHandler, ExceptionInterceptor}

import java.io.File
import scala.concurrent.ExecutionContext

/** @tparam F The effect type used for response body streams. Usually the same as `G`.
  * @tparam G The effect type used for representing arbitrary side-effects, such as creating files or logging.
  *           Usually the same as `F`.
  */
case class Http4sServerOptions[F[_], G[_]](
    createFile: ServerRequest => G[File],
    blockingExecutionContext: ExecutionContext,
    ioChunkSize: Int,
    interceptors: List[Interceptor[G, Http4sResponseBody[F]]]
) {
  def prependInterceptor(i: Interceptor[G, Http4sResponseBody[F]]): Http4sServerOptions[F, G] =
    copy(interceptors = i :: interceptors)
  def appendInterceptor(i: Interceptor[G, Http4sResponseBody[F]]): Http4sServerOptions[F, G] =
    copy(interceptors = interceptors :+ i)
}

object Http4sServerOptions {

  /** Creates default [[Http4sServerOptions]] with custom interceptors, sitting between an optional exception
    * interceptor, optional logging interceptor, and the ultimate decode failure handling interceptor.
    *
    * The options can be then further customised using copy constructors or the methods to append/prepend
    * interceptors.
    *
    * @param exceptionHandler Whether to respond to exceptions, or propagate them to http4s.
    * @param serverLog The server log using which an interceptor will be created, if any. To keep the default, use
    *                  `Http4sServerOptions.Log.defaultServerLog`
    * @param additionalInterceptors Additional interceptors, e.g. handling decode failures, or providing alternate
    *                               responses.
    * @param decodeFailureHandler The decode failure handler, from which an interceptor will be created.
    */
  def customInterceptors[F[_], G[_]: Sync: ContextShift](
      exceptionHandler: Option[ExceptionHandler],
      serverLog: Option[ServerLog[G[Unit]]],
      additionalInterceptors: List[Interceptor[G, Http4sResponseBody[F]]] = Nil,
      decodeFailureHandler: DecodeFailureHandler = DefaultDecodeFailureHandler.handler
  ): Http4sServerOptions[F, G] =
    Http4sServerOptions(
      defaultCreateFile[G].apply(ExecutionContext.Implicits.global),
      ExecutionContext.Implicits.global,
      8192,
      exceptionHandler.map(new ExceptionInterceptor[G, Http4sResponseBody[F]](_)).toList ++
        serverLog.map(Log.serverLogInterceptor[F, G]).toList ++
        additionalInterceptors ++
        List(new DecodeFailureInterceptor[G, Http4sResponseBody[F]](decodeFailureHandler))
    )

  def defaultCreateFile[F[_]](implicit sync: Sync[F]): ExecutionContext => ServerRequest => F[File] =
    ec => _ => cs.evalOn(ec)(sync.delay(Defaults.createTempFile()))

  object Log {
    def defaultServerLog[F[_]: Sync]: ServerLog[F[Unit]] =
      DefaultServerLog[F[Unit]](
        doLogWhenHandled = debugLog[F],
        doLogAllDecodeFailures = debugLog[F],
        doLogExceptions = (msg: String, ex: Throwable) => Sync[F].delay(Http4sServerInterpreter.log.error(ex)(msg)),
        noLog = Applicative[F].unit
      )

    def serverLogInterceptor[F[_], G[_]](serverLog: ServerLog[G[Unit]]): ServerLogInterceptor[G[Unit], G, Http4sResponseBody[F]] =
      new ServerLogInterceptor[G[Unit], G, Http4sResponseBody[F]](serverLog, (f, _) => f)

    private def debugLog[F[_]: Sync](msg: String, exOpt: Option[Throwable]): F[Unit] =
      exOpt match {
        case None     => Sync[F].delay(Http4sServerInterpreter.log.debug(msg))
        case Some(ex) => Sync[F].delay(Http4sServerInterpreter.log.debug(ex)(msg))
      }
  }

  implicit def default[F[_], G[_]: Sync: ContextShift]: Http4sServerOptions[F, G] =
    customInterceptors(Some(DefaultExceptionHandler), Some(Log.defaultServerLog[G]))
}
