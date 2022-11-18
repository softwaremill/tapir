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

  def toHttp[R2](se: ZServerEndpoint[R2, ZioStreams]): Http[R & R2, Throwable, Request, Response] =
    toHttp(List(se))

  def toHttp[R2](ses: List[ZServerEndpoint[R2, ZioStreams]]): Http[R & R2, Throwable, Request, Response] = {
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

    Http.collectHttp[Request] { case req =>
      Http.fromZIO {
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
      }.flatten
    }
  }

  private def sttpToZioHttpHeader(hl: (String, Seq[SttpHeader])): ZioHttpHeader =
    (hl._1, hl._2.map(f => f.value).mkString(", "))
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
