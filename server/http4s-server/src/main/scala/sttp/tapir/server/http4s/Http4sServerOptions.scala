package sttp.tapir.server.http4s

import cats.Applicative
import cats.effect.{ContextShift, Sync}
import sttp.tapir.Defaults
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.{DecodeFailureInterceptor, EndpointInterceptor, LogInterceptor}
import sttp.tapir.server.{DecodeFailureHandler, DefaultLogRequestHandling, LogRequestHandling, ServerDefaults}

import java.io.File
import scala.concurrent.ExecutionContext

case class Http4sServerOptions[F[_], G[_]](
    createFile: ServerRequest => G[File],
    blockingExecutionContext: ExecutionContext,
    ioChunkSize: Int,
    interceptors: List[EndpointInterceptor[G, Http4sResponseBody[F]]]
) {
  def prependInterceptor(i: EndpointInterceptor[G, Http4sResponseBody[F]]): Http4sServerOptions[F, G] =
    copy(interceptors = i :: interceptors)
  def appendInterceptor(i: EndpointInterceptor[G, Http4sResponseBody[F]]): Http4sServerOptions[F, G] =
    copy(interceptors = interceptors :+ i)
}

object Http4sServerOptions {
  def default[F[_], G[_]: Sync: ContextShift](
      logRequestHandling: LogRequestHandling[G[Unit]],
      decodeFailureHandler: DecodeFailureHandler
  ): Http4sServerOptions[F, G] =
    Http4sServerOptions(
      defaultCreateFile[G].apply(ExecutionContext.Implicits.global),
      ExecutionContext.Implicits.global,
      8192,
      List(
        new LogInterceptor[G[Unit], G, Http4sResponseBody[F]](logRequestHandling, (f, _) => f),
        new DecodeFailureInterceptor(decodeFailureHandler)
      )
    )

  def defaultCreateFile[F[_]](implicit sync: Sync[F], cs: ContextShift[F]): ExecutionContext => ServerRequest => F[File] =
    ec => _ => cs.evalOn(ec)(sync.delay(Defaults.createTempFile()))

  def defaultLogRequestHandling[F[_]: Sync]: LogRequestHandling[F[Unit]] =
    DefaultLogRequestHandling[F[Unit]](
      doLogWhenHandled = debugLog[F],
      doLogAllDecodeFailures = debugLog[F],
      doLogLogicExceptions = (msg: String, ex: Throwable) => Sync[F].delay(Http4sServerInterpreter.log.error(ex)(msg)),
      noLog = Applicative[F].unit
    )

  implicit def defaultOptions[F[_], G[_]: Sync: ContextShift]: Http4sServerOptions[F, G] = default(
    defaultLogRequestHandling[G],
    ServerDefaults.decodeFailureHandler
  )

  private def debugLog[F[_]: Sync](msg: String, exOpt: Option[Throwable]): F[Unit] =
    exOpt match {
      case None     => Sync[F].delay(Http4sServerInterpreter.log.debug(msg))
      case Some(ex) => Sync[F].delay(Http4sServerInterpreter.log.debug(ex)(msg))
    }
}
