package sttp.tapir.server.ziohttp

import io.netty.handler.codec.http.HttpResponseStatus
import sttp.capabilities.zio.ZioStreams
import sttp.model.{HeaderNames, Header => SttpHeader}
import sttp.monad.MonadError
import sttp.tapir.server.interceptor.{DecodeFailureContext, RequestResult}
import sttp.tapir.server.interceptor.reject.RejectInterceptor
import sttp.tapir.server.interpreter.{
  DecodeBasicInputs,
  DecodeBasicInputsResult,
  DecodeInputsContext,
  FilterServerEndpoints,
  ServerInterpreter
}
import sttp.tapir.ztapir._
import zio.http.{Body, Handler, HttpApp, Http, Request, Response}
import zio.http.model.{Status, Header => ZioHttpHeader, Headers => ZioHttpHeaders, HttpError}
import zio._

trait ZioHttpInterpreter[R] {
  def zioHttpServerOptions: ZioHttpServerOptions[R] = ZioHttpServerOptions.default

  def toHttp[R2](se: ZServerEndpoint[R2, ZioStreams]): HttpApp[R & R2, Throwable] =
    toHttp(List(se))

  def toHttp[R2](ses: List[ZServerEndpoint[R2, ZioStreams]]): HttpApp[R & R2, Throwable] = {
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

    def handleRequest(req: Request) = {
      Handler.fromZIO {
        interpreter
          .apply(ZioHttpServerRequest(req))
          .foldZIO(
            error => ZIO.fail(error),
            {
              case RequestResult.Response(resp) =>
                val baseHeaders = resp.headers.groupBy(_.name).flatMap(sttpToZioHttpHeader).toList
                val allHeaders = resp.body match {
                  case Some((_, Some(contentLength))) if resp.contentLength.isEmpty =>
                    ZioHttpHeader(HeaderNames.ContentLength, contentLength.toString) :: baseHeaders
                  case _ => baseHeaders
                }

                ZIO.succeed(
                  Response(
                    status = Status.fromHttpResponseStatus(HttpResponseStatus.valueOf(resp.code.code)),
                    headers = ZioHttpHeaders(allHeaders),
                    body = resp.body.map { case (stream, _) => Body.fromStream(stream) }.getOrElse(Body.empty)
                  )
                )
              case RequestResult.Failure(_) => ZIO.succeed(HttpError.NotFound("Not Found").toResponse)
            }
          )
      }
    }

    val routes = new PartialFunction[Request, Handler[R & R2, Throwable, Request, Response]] {
      override def isDefinedAt(request: Request): Boolean = {
        val serverRequest = ZioHttpServerRequest(request)
        ses.exists { se =>
          DecodeBasicInputs(se.securityInput.and(se.input), DecodeInputsContext(serverRequest)) match {
            case (DecodeBasicInputsResult.Values(_, _), _) => true
            case (DecodeBasicInputsResult.Failure(input, failure), _) =>
              zioHttpServerOptions.decodeFailureHandler(DecodeFailureContext(se.endpoint, input, failure, serverRequest)).isDefined
          }
        }
      }

      override def apply(req: Request) = handleRequest(req)
    }

    Http.collectHandler[Request](routes)
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
