package sttp.tapir.server.ziohttp

import io.netty.handler.codec.http.HttpResponseStatus
import sttp.capabilities.zio.ZioStreams
import sttp.model.{HeaderNames, Header => SttpHeader}
import sttp.monad.MonadError
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.interceptor.reject.RejectInterceptor
import sttp.tapir.server.interpreter.{FilterServerEndpoints, ServerInterpreter}
import sttp.tapir.ztapir._
import zhttp.http.{Http, HttpData, Request, Response, Status, Header => ZioHttpHeader, Headers => ZioHttpHeaders}
import zio._

trait ZioHttpInterpreter[R] {

  def zioHttpServerOptions: ZioHttpServerOptions[R] = ZioHttpServerOptions.default

  def toHttp(se: ZServerEndpoint[R, ZioStreams]): Http[R, Throwable, Request, Response] =
    toHttp(List(se))

  def toHttp(ses: List[ZServerEndpoint[R, ZioStreams]]): Http[R, Throwable, Request, Response] = {
    implicit val bodyListener: ZioHttpBodyListener[R] = new ZioHttpBodyListener[R]
    implicit val monadError: MonadError[RIO[R, *]] = new RIOMonadError[R]
    val interpreter = new ServerInterpreter[ZioStreams, RIO[R, *], ZioHttpResponseBody, ZioStreams](
      FilterServerEndpoints(ses),
      new ZioHttpRequestBody(zioHttpServerOptions),
      new ZioHttpToResponseBody,
      RejectInterceptor.disableWhenSingleEndpoint(zioHttpServerOptions.interceptors, ses),
      zioHttpServerOptions.deleteFile
    )

    Http.collectHttp[Request] { case req =>
      Http
        .fromZIO(
          interpreter
            .apply(ZioHttpServerRequest(req))
            .map {
              case RequestResult.Response(resp) =>
                val baseHeaders = resp.headers.groupBy(_.name).map(sttpToZioHttpHeader).toList
                val allHeaders = resp.body match {
                  case Some((_, Some(contentLength))) if resp.contentLength.isEmpty =>
                    (HeaderNames.ContentLength, contentLength.toString) :: baseHeaders
                  case _ => baseHeaders
                }

                Http.succeed(
                  Response(
                    status = Status.fromHttpResponseStatus(HttpResponseStatus.valueOf(resp.code.code)),
                    headers = ZioHttpHeaders(allHeaders),
                    data = resp.body.map { case (stream, _) => HttpData.fromStream(stream) }.getOrElse(HttpData.empty)
                  )
                )
              case RequestResult.Failure(_) => Http.empty
            }
        )
        .flatten
    }
  }

  private def sttpToZioHttpHeader(hl: (String, Seq[SttpHeader])): ZioHttpHeader =
    (hl._1, hl._2.map(f => f.value).mkString(", "))

}

object ZioHttpInterpreter {
  def apply[R](serverOptions: ZioHttpServerOptions[R] = ZioHttpServerOptions.default[R]): ZioHttpInterpreter[R] = {
    new ZioHttpInterpreter[R] {
      override def zioHttpServerOptions: ZioHttpServerOptions[R] = serverOptions
    }
  }
}
