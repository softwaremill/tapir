package sttp.tapir.server.ziohttp

import io.netty.handler.codec.http.HttpResponseStatus
import sttp.capabilities.zio.ZioStreams
import sttp.model.{Header => SttpHeader}
import sttp.monad.MonadError
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.interpreter.{FilterServerEndpoints, ServerInterpreter}
import sttp.tapir.ztapir._
import zhttp.http.{Http, HttpData, Request, Response, Status, Header => ZioHttpHeader, Headers => ZioHttpHeaders}
import zio._
import zio.stream.Stream

trait ZioHttpInterpreter[R] {

  def zioHttpServerOptions: ZioHttpServerOptions[R] = ZioHttpServerOptions.default

  def toHttp(se: ZServerEndpoint[R, ZioStreams]): Http[R, Throwable, Request, Response] =
    toHttp(List(se))

  def toHttp(ses: List[ZServerEndpoint[R, ZioStreams]]): Http[R, Throwable, Request, Response] = {
    implicit val bodyListener: ZioHttpBodyListener[R] = new ZioHttpBodyListener[R]
    implicit val monadError: MonadError[RIO[R, *]] = new RIOMonadError[R]
    val interpreter = new ServerInterpreter[ZioStreams, RIO[R, *], Stream[Throwable, Byte], ZioStreams](
      FilterServerEndpoints(ses),
      new ZioHttpRequestBody(zioHttpServerOptions),
      new ZioHttpToResponseBody,
      zioHttpServerOptions.interceptors,
      zioHttpServerOptions.deleteFile
    )

    Http.route[Request] { case req =>
      Http
        .fromZIO(
          interpreter
            .apply(new ZioHttpServerRequest(req))
            .map {
              case RequestResult.Response(resp) =>
                Http.succeed(
                  Response(
                    status = Status.fromHttpResponseStatus(HttpResponseStatus.valueOf(resp.code.code)),
                    headers = ZioHttpHeaders(resp.headers.groupBy(_.name).map(sttpToZioHttpHeader).toList),
                    data = resp.body.map(stream => HttpData.fromStream(stream)).getOrElse(HttpData.empty)
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
