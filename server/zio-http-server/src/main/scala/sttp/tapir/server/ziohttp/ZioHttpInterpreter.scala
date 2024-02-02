package sttp.tapir.server.ziohttp

import sttp.capabilities.WebSockets
import sttp.capabilities.zio.ZioStreams
import sttp.model.Method
import sttp.model.{Header => SttpHeader}
import sttp.monad.MonadError
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.interceptor.reject.RejectInterceptor
import sttp.tapir.server.interpreter.FilterServerEndpoints
import sttp.tapir.server.interpreter.ServerInterpreter
import sttp.tapir.server.model.ServerResponse
import sttp.tapir.ztapir._
import zio._
import zio.http.{Header => ZioHttpHeader}
import zio.http.{Headers => ZioHttpHeaders}
import zio.http._

trait ZioHttpInterpreter[R] {
  def zioHttpServerOptions: ZioHttpServerOptions[R] = ZioHttpServerOptions.default

  def toHttp[R2](se: ZServerEndpoint[R2, ZioStreams with WebSockets]): HttpApp[R & R2] =
    toHttp(List(se))

  def toHttp[R2](ses: List[ZServerEndpoint[R2, ZioStreams with WebSockets]]): HttpApp[R & R2] = {
    implicit val bodyListener: ZioHttpBodyListener[R & R2] = new ZioHttpBodyListener[R & R2]
    implicit val monadError: MonadError[RIO[R & R2, *]] = new RIOMonadError[R & R2]
    val widenedSes = ses.map(_.widen[R & R2])
    val widenedServerOptions = zioHttpServerOptions.widen[R & R2]
    val zioHttpRequestBody = new ZioHttpRequestBody(widenedServerOptions)
    val zioHttpResponseBody = new ZioHttpToResponseBody
    val interceptors = RejectInterceptor.disableWhenSingleEndpoint(widenedServerOptions.interceptors, widenedSes)

    HttpApp(
      Routes.singleton {
        Handler.fromFunctionZIO[(Path, Request)] { case (_: Path, request: Request) =>
          val interpreter = new ServerInterpreter[ZioStreams with WebSockets, RIO[R & R2, *], ZioResponseBody, ZioStreams](
            FilterServerEndpoints(widenedSes),
            zioHttpRequestBody,
            zioHttpResponseBody,
            interceptors,
            zioHttpServerOptions.deleteFile
          )

          if (request.url.encode.trim.isEmpty) {
            ZIO.logError("Received an apparently empty request URI, not handling: " + request) *>
              ZIO.fail(Response.internalServerError("Empty request URI"))
          } else {
            val serverRequest = ZioHttpServerRequest(request)
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
                    ZIO.fail(Response.notFound)
                }
              )
          }
        }
      }
    )
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
        status = Status.fromInt(statusCode).getOrElse(Status.Custom(statusCode)),
        headers = ZioHttpHeaders(allHeaders),
        body = body
          .map {
            case ZioStreamHttpResponseBody(stream, _) => Body.fromStream(stream)
            case ZioRawHttpResponseBody(chunk, _)     => Body.fromChunk(chunk)
          }
          .getOrElse(Body.empty)
      )
    )
  }

  private def sttpToZioHttpHeader(hl: (String, Seq[SttpHeader])): List[ZioHttpHeader] =
    List(ZioHttpHeader.Custom(hl._1, hl._2.map(_.value).mkString(", ")))
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
