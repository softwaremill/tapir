package sttp.tapir.server.ziohttp

import io.netty.handler.codec.http.HttpResponseStatus
import sttp.capabilities.zio.ZioStreams
import sttp.model.{HeaderNames, Header => SttpHeader}
import sttp.monad.MonadError
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.interceptor.reject.RejectInterceptor
import sttp.tapir.server.interpreter.{FilterServerEndpoints, ServerInterpreter}
import sttp.tapir.ztapir._
import zio.http.{App, Body, Handler, Http, Request, Response}
import zio.http.model.{Status, Header => ZioHttpHeader, Headers => ZioHttpHeaders}
import zio._

trait ZioHttpInterpreter[R] {
  def zioHttpServerOptions: ZioHttpServerOptions[R] = ZioHttpServerOptions.default

  def toApp[R2](se: ZServerEndpoint[R2, ZioStreams]): App[R & R2] =
    toApp(List(se))

  def toApp[R2](ses: List[ZServerEndpoint[R2, ZioStreams]]): App[R & R2] = {
    implicit val bodyListener: ZioHttpBodyListener[R & R2] = new ZioHttpBodyListener[R & R2]
    implicit val monadError: MonadError[RIO[R & R2, *]] = new RIOMonadError[R & R2]
    val widenedSes = ses.map(_.widen[R & R2])
    val widenedServerOptions = zioHttpServerOptions.widen[R & R2]

    val interpreter = new ServerInterpreter[ZioStreams, RIO[R & R2, *], ZioHttpResponseBody, ZioStreams](
      FilterServerEndpoints[ZioStreams, RIO[R & R2, *]](widenedSes),
      new ZioHttpRequestBody(widenedServerOptions),
      new ZioHttpToResponseBody,
      RejectInterceptor.disableWhenSingleEndpoint(widenedServerOptions.interceptors, widenedSes),
      zioHttpServerOptions.deleteFile
    )

    Http
      .fromOptionalHandlerZIO[Request] { req =>
        interpreter
          .apply(ZioHttpServerRequest(req))
          .foldZIO(
            error => ZIO.fail(Some(error)),
            {
              case RequestResult.Response(resp) =>
                val baseHeaders = resp.headers.groupBy(_.name).flatMap(sttpToZioHttpHeader).toList
                val allHeaders = resp.body match {
                  case Some((_, Some(contentLength))) if resp.contentLength.isEmpty =>
                    ZioHttpHeader(HeaderNames.ContentLength, contentLength.toString) :: baseHeaders
                  case _ => baseHeaders
                }

                ZIO.succeed(
                  Handler.response(
                    Response(
                      status = Status.fromHttpResponseStatus(HttpResponseStatus.valueOf(resp.code.code)),
                      headers = ZioHttpHeaders(allHeaders),
                      body = resp.body.map { case (stream, _) => Body.fromStream(stream) }.getOrElse(Body.empty)
                    )
                  )
                )
              case RequestResult.Failure(_) => ZIO.fail(None)
            }
          )
      }
      .withDefaultErrorResponse
  }

  private def sttpToZioHttpHeader(hl: (String, Seq[SttpHeader])): List[ZioHttpHeader] = {
    hl._2.map(h => ZioHttpHeader(h.name, h.value)).toList
  }
}

object ZioHttpInterpreter {
  def apply[R](serverOptions: ZioHttpServerOptions[R]): ZioHttpInterpreter[R] = {
    new ZioHttpInterpreter[R] {
      override def zioHttpServerOptions: ZioHttpServerOptions[R] = serverOptions
    }
  }
  def apply(): ZioHttpInterpreter[Any] = {
    new ZioHttpInterpreter[Any] {
      override def zioHttpServerOptions: ZioHttpServerOptions[Any] = ZioHttpServerOptions.default[Any]
    }
  }
}
