package sttp.tapir.server.zhttp

import io.netty.handler.codec.http.HttpResponseStatus
import sttp.capabilities.zio.ZioStreams
import sttp.model.{Header => SttpHeader}
import sttp.tapir.Endpoint
import sttp.tapir.server.interpreter.ServerInterpreter
import sttp.tapir.ztapir.ZIOMonadError
import zhttp.http.{Http, HttpData, HttpError, Request, Response, Status, Header => ZHttpHeader}
import zio._
import zio.blocking.Blocking
import zio.stream._

object ZHttpInterpreter extends ZIOMonadError {

  private def sttpToZHttpHeader(header: SttpHeader): ZHttpHeader =
    ZHttpHeader(header.name, header.value)

  def toHttp[I, O, R <: Blocking](
      route: Endpoint[I, Throwable, O, ZioStreams]
  )(logic: I => RIO[R, O]): Http[R, Throwable, Request, Response[R, Throwable]] = {
    Http.fromEffectFunction[Request] { req =>
      implicit val interpret: ZHttpBodyListener[R] = new ZHttpBodyListener[R]
      val router = route.serverLogic[RIO[R, *]](input => logic(input).either)
      val interpreter = new ServerInterpreter[ZioStreams, RIO[R, *], ZStream[Blocking, Throwable, Byte], ZioStreams](
        new ZHttpRequestBody(req),
        new ZHttpToResponseBody,
        Nil,
        (t) => RIO.apply(print("t"))
      )

      interpreter.apply(new ZHttpServerRequest(req), router).flatMap {
        case Some(resp) =>
          ZIO.succeed(
            Response.HttpResponse(
              status = Status.fromJHttpResponseStatus(HttpResponseStatus.valueOf(resp.code.code)),
              headers = resp.headers.map(sttpToZHttpHeader).toList,
              content = resp.body.map(stream => HttpData.fromStream(stream)).getOrElse(HttpData.empty)
            )
          )
        case None => ZIO.fail(HttpError.NotFound(req.url.path))
      }
    }
  }
}
