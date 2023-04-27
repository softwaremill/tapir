package sttp.tapir.server.ziohttp

import sttp.capabilities.zio.ZioStreams
import sttp.model.{Method, Header => SttpHeader}
import sttp.monad.MonadError
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.interceptor.reject.RejectInterceptor
import sttp.tapir.server.interpreter.{FilterServerEndpoints, ServerInterpreter}
import sttp.tapir.ztapir._
import zio._
import zio.http.{Header => ZioHttpHeader, Headers => ZioHttpHeaders, _}

trait ZioHttpInterpreter[R] {
  def zioHttpServerOptions: ZioHttpServerOptions[R] = ZioHttpServerOptions.default

  def toHttp[R2](se: ZServerEndpoint[R2, ZioStreams]): HttpApp[R & R2, Throwable] =
    toHttp(List(se))

  def toHttp[R2](ses: List[ZServerEndpoint[R2, ZioStreams]]): HttpApp[R & R2, Throwable] = {
    implicit val bodyListener: ZioHttpBodyListener[R & R2] = new ZioHttpBodyListener[R & R2]
    implicit val monadError: MonadError[RIO[R & R2, *]] = new RIOMonadError[R & R2]
    val widenedSes = ses.map(_.widen[R & R2])
    val widenedServerOptions = zioHttpServerOptions.widen[R & R2]
    val zioHttpRequestBody = new ZioHttpRequestBody(widenedServerOptions)
    val zioHttpResponseBody = new ZioHttpToResponseBody
    val interceptors = RejectInterceptor.disableWhenSingleEndpoint(widenedServerOptions.interceptors, widenedSes)

    def handleRequest(req: Request, filteredEndpoints: List[ZServerEndpoint[R & R2, ZioStreams]]) = {
      Handler.fromZIO {
        val interpreter = new ServerInterpreter[ZioStreams, RIO[R & R2, *], ZioHttpResponseBody, ZioStreams](
          _ => filteredEndpoints,
          zioHttpRequestBody,
          zioHttpResponseBody,
          interceptors,
          zioHttpServerOptions.deleteFile
        )

        interpreter
          .apply(ZioHttpServerRequest(req))
          .foldZIO(
            error => ZIO.fail(error),
            {
              case RequestResult.Response(resp) =>
                val baseHeaders = resp.headers.groupBy(_.name).flatMap(sttpToZioHttpHeader).toList
                val allHeaders = resp.body match {
                  case Some((_, Some(contentLength))) if resp.contentLength.isEmpty =>
                    ZioHttpHeader.ContentLength(contentLength) :: baseHeaders
                  case _ => baseHeaders
                }
                val statusCode = resp.code.code

                ZIO.succeed(
                  Response(
                    status = Status.fromInt(statusCode).getOrElse(Status.Custom(statusCode)),
                    headers = ZioHttpHeaders(allHeaders),
                    body = resp.body.map { case (stream, _) => Body.fromStream(stream) }.getOrElse(Body.empty)
                  )
                )
              case RequestResult.Failure(_) =>
                ZIO.fail(
                  new RuntimeException(
                    s"The path: ${req.path} matches the shape of some endpoint, but none of the " +
                      s"endpoints decoded the request successfully, and the decode failure handler didn't provide a " +
                      s"response. ZIO Http requires that if the path shape matches some endpoints, the request " +
                      s"should be handled by tapir."
                  )
                )
            }
          )
      }
    }

    val serverEndpointsFilter = FilterServerEndpoints[ZioStreams, RIO[R & R2, *]](widenedSes)
    val singleEndpoint = widenedSes.size == 1

    Http.fromOptionalHandlerZIO { request =>
      // pre-filtering the endpoints by shape to determine, if this request should be handled by tapir
      val filteredEndpoints = serverEndpointsFilter.apply(ZioHttpServerRequest(request))
      val filteredEndpoints2 = if (singleEndpoint) {
        // If we are interpreting a single endpoint, we verify that the method matches as well; in case it doesn't,
        // we refuse to handle the request, allowing other ZIO Http routes to handle it. Otherwise even if the method
        // doesn't match, this will be handled by the RejectInterceptor
        filteredEndpoints.filter { e =>
          val m = e.endpoint.method
          m.isEmpty || m.contains(Method(request.method.toString()))
        }
      } else filteredEndpoints

      filteredEndpoints2 match {
        case Nil => ZIO.fail(None)
        case _   => ZIO.succeed(handleRequest(request, filteredEndpoints2))
      }
    }
  }

  private def sttpToZioHttpHeader(hl: (String, Seq[SttpHeader])): List[ZioHttpHeader] =
    List(ZioHttpHeader.Custom(hl._1, hl._2.map(_.value).mkString(", ")))
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
