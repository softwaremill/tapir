package sttp.tapir.server.ziohttp

import sttp.capabilities.WebSockets
import sttp.capabilities.zio.ZioStreams
import sttp.model.{Header => SttpHeader}
import sttp.monad.MonadError
import sttp.tapir.EndpointInput
import sttp.tapir.internal.RichEndpointInput
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.interceptor.reject.RejectInterceptor
import sttp.tapir.server.interpreter.ServerInterpreter
import sttp.tapir.server.model.ServerResponse
import sttp.tapir.ztapir._
import zio._
import zio.http.codec.PathCodec
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

              case RequestResult.Failure(_) => ZIO.succeed(Response.notFound)
            }
          )
      }

    // Grouping the endpoints by path prefix template (fixed path components & single path captures). This way, if
    // there are multiple endpoints - with/without trailing slash, with from-request extraction, or with path wildcards,
    // they will be interpreted and disambiguated by the tapir logic, instead of ZIO HTTP's routing. Also, this covers
    // multiple endpoints with different methods, and allows us to handle invalid methods.
    val widenedSesGroupedByPathPrefixTemplate = widenedSes.groupBy { se =>
      val e = se.endpoint
      val inputs = e.securityInput.and(e.input).asVectorOfBasicInputs()
      val x = inputs.foldLeft("") { case (p, component) =>
        component match {
          case _: EndpointInput.PathCapture[_] => p + "/?"
          case i: EndpointInput.FixedPath[_]   => p + "/" + i.s
          case _                               => p
        }
      }
      x
    }

    val handlers: List[Route[R & R2, Response]] = widenedSesGroupedByPathPrefixTemplate.toList.map { case (_, sesForPathTemplate) =>
      // The pattern that we generate should be the same for all endpoints in a group
      val e = sesForPathTemplate.head.endpoint
      val inputs = e.securityInput.and(e.input).asVectorOfBasicInputs()

      val hasPath = inputs.exists {
        case _: EndpointInput.PathCapture[_]  => true
        case _: EndpointInput.PathsCapture[_] => true
        case _: EndpointInput.FixedPath[_]    => true
        case _                                => false
      }

      val pathPrefixPattern = if (hasPath) {
        val initialPattern = RoutePattern(Method.ANY, PathCodec.empty).asInstanceOf[RoutePattern[Any]]
        val base = inputs
          .foldLeft(initialPattern) { case (p, component) =>
            component match {
              case i: EndpointInput.PathCapture[_] => (p / PathCodec.string(i.name.getOrElse("?"))).asInstanceOf[RoutePattern[Any]]
              case i: EndpointInput.FixedPath[_]   => p / PathCodec.literal(i.s)
              case _                               => p
            }
          }
        // because we capture the path prefix, we add a matcher for arbitrary other path components (which might be
        // handled by tapir's `paths` or `extractFromRequest`)
        base / PathCodec.trailing
      } else {
        // if there are no path inputs, we return a catch-all
        RoutePattern(Method.ANY, PathCodec.trailing).asInstanceOf[RoutePattern[Any]]
      }

      Route.handled(pathPrefixPattern)(Handler.fromFunctionHandler { (request: Request) => handleRequest(request, sesForPathTemplate) })
    }

    Routes(Chunk.fromIterable(handlers))
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
