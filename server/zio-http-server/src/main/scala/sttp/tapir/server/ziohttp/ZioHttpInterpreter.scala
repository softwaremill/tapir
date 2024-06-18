package sttp.tapir.server.ziohttp

import sttp.capabilities.WebSockets
import sttp.capabilities.zio.ZioStreams
import sttp.model.{Header => SttpHeader}
import sttp.monad.MonadError
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.interceptor.reject.RejectInterceptor
import sttp.tapir.server.interpreter.{FilterServerEndpoints, ServerInterpreter}
import sttp.tapir.server.model.ServerResponse
import sttp.tapir.ztapir._
import zio._
import zio.http.{Header => ZioHttpHeader, Headers => ZioHttpHeaders, _}

trait ZioHttpInterpreter[R] {
  def zioHttpServerOptions: ZioHttpServerOptions[R] = ZioHttpServerOptions.default

  def toHttp[R2](se: ZServerEndpoint[R2, ZioStreams with WebSockets]): Routes[R & R2, Response] =
    toHttp(List(se))

  def toHttp[R2](ses: List[ZServerEndpoint[R2, ZioStreams with WebSockets]]): Routes[R & R2, Response] = {
    implicit val bodyListener: ZioHttpBodyListener[R & R2] = new ZioHttpBodyListener[R & R2]
    implicit val monadError: MonadError[RIO[R & R2, *]] = new RIOMonadError[R & R2]
    val widenedSes = ses.map(_.widen[R & R2])
    val widenedServerOptions = zioHttpServerOptions.widen[R & R2]
    val zioHttpRequestBody = new ZioHttpRequestBody(widenedServerOptions)
    val zioHttpResponseBody = new ZioHttpToResponseBody
    val interceptors = RejectInterceptor.disableWhenSingleEndpoint(widenedServerOptions.interceptors, widenedSes)

    def handleRequest(req: Request, filteredEndpoints: List[ZServerEndpoint[R & R2, ZioStreams with WebSockets]]) =
      Handler.fromZIO {
        val interpreter = new ServerInterpreter[ZioStreams with WebSockets, RIO[R & R2, *], ZioResponseBody, ZioStreams](
          _ => filteredEndpoints,
          zioHttpRequestBody,
          zioHttpResponseBody,
          interceptors,
          zioHttpServerOptions.deleteFile
        )
        val serverRequest = ZioHttpServerRequest(req)

        interpreter
          .apply(serverRequest)
          .foldCauseZIO(
            cause => ZIO.logErrorCause(cause) *> ZIO.fail(Response.internalServerError(cause.squash.getMessage)),
            {
              case RequestResult.Response(resp) =>
                resp.body match {
                  case None              => handleHttpResponse(resp, None)
                  case Some(Right(body)) => handleHttpResponse(resp, Some(body))
                  case Some(Left(body))  => handleWebSocketResponse(body, zioHttpServerOptions.customWebSocketConfig(serverRequest))
                }

              case RequestResult.Failure(_) =>
                ZIO.logError(
                  s"The path: ${req.path} matches the shape of some endpoint, but none of the " +
                    s"endpoints decoded the request successfully, and the decode failure handler didn't provide a " +
                    s"response. ZIO Http requires that if the path shape matches some endpoints, the request " +
                    s"should be handled by tapir."
                ) *> ZIO.fail(Response.internalServerError)
            }
          )
      }

    val serverEndpointsFilter = FilterServerEndpoints[ZioStreams with WebSockets, RIO[R & R2, *]](widenedSes)
    val singleEndpoint = widenedSes.size == 1

    Routes.singleton {
      Handler.fromFunctionHandler { case (_: Path, request: Request) =>
        // pre-filtering the endpoints by shape to determine, if this request should be handled by tapir
        val filteredEndpoints = serverEndpointsFilter.apply(ZioHttpServerRequest(request))
        val filteredEndpoints2 = if (singleEndpoint) {
          // If we are interpreting a single endpoint, we verify that the method matches as well; in case it doesn't,
          // we refuse to handle the request, allowing other ZIO Http routes to handle it. Otherwise even if the method
          // doesn't match, this will be handled by the RejectInterceptor
          filteredEndpoints.filter { e =>
            val m = e.endpoint.method
            m.isEmpty || m.contains(sttp.model.Method(request.method.toString()))
          }
        } else filteredEndpoints

        filteredEndpoints2 match {
          case Nil => Handler.fail(Response.notFound)
          case _   => handleRequest(request, filteredEndpoints2)
        }
      }
    }
  }

  private def handleWebSocketResponse(
      webSocketHandler: WebSocketHandler,
      webSocketConfig: Option[WebSocketConfig]
  ): ZIO[Any, Nothing, Response] = {
    val app = Handler.webSocket { channel =>
      for {
        channelEventsQueue <- zio.Queue.unbounded[WebSocketChannelEvent]
        messageReceptionFiber <- channel.receiveAll { message => channelEventsQueue.offer(message) }.fork
        webSocketStream <- webSocketHandler(stream.ZStream.fromQueue(channelEventsQueue))
        _ <- webSocketStream.mapZIO(channel.send).runDrain
      } yield messageReceptionFiber.join
    }
    webSocketConfig.fold(app)(app.withConfig).toResponse
  }

  private def handleHttpResponse(
      resp: ServerResponse[ZioResponseBody],
      body: Option[ZioHttpResponseBody]
  ): UIO[Response] = {
    val baseHeaders = resp.headers.groupBy(_.name).flatMap(sttpToZioHttpHeader).toList
    val allHeaders = body.flatMap(_.contentLength) match {
      case Some(contentLength) if resp.contentLength.isEmpty => ZioHttpHeader.ContentLength(contentLength) :: baseHeaders
      case _                                                 => baseHeaders
    }
    val statusCode = resp.code.code

    ZIO.succeed(
      Response(
        status = Status.fromInt(statusCode),
        headers = ZioHttpHeaders(allHeaders),
        body = body
          .map {
            case ZioStreamHttpResponseBody(stream, Some(contentLength)) => Body.fromStream(stream, contentLength)
            case ZioStreamHttpResponseBody(stream, None)                => Body.fromStreamChunked(stream)
            case ZioRawHttpResponseBody(chunk, _)                       => Body.fromChunk(chunk)
          }
          .getOrElse(Body.empty)
      )
    )
  }

  private def sttpToZioHttpHeader(hl: (String, Seq[SttpHeader])): Seq[ZioHttpHeader] = {
    hl._1.toLowerCase match {
      case "set-cookie" =>
        hl._2.map(_.value).map { rawValue =>
          ZioHttpHeader.SetCookie.parse(rawValue).toOption.getOrElse {
            ZioHttpHeader.Custom(hl._1, rawValue)
          }
        }
      case _ => List(ZioHttpHeader.Custom(hl._1, hl._2.map(_.value).mkString(", ")))
    }
  }
}

object ZioHttpInterpreter {

  def apply[R](serverOptions: ZioHttpServerOptions[R]): ZioHttpInterpreter[R] =
    new ZioHttpInterpreter[R] {
      override def zioHttpServerOptions: ZioHttpServerOptions[R] = serverOptions
    }
  def apply(): ZioHttpInterpreter[Any] =
    new ZioHttpInterpreter[Any] {
      override def zioHttpServerOptions: ZioHttpServerOptions[Any] = ZioHttpServerOptions.default[Any]
    }
}
