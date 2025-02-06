package sttp.tapir.server.pekkohttp

import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.server.Directives.{
  complete,
  extractExecutionContext,
  extractMaterializer,
  extractRequestContext,
  handleWebSocketMessages,
  onSuccess,
  reject,
  respondWithHeaders
}
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import sttp.capabilities.WebSockets
import sttp.capabilities.pekko.PekkoStreams
import sttp.model.Method
import sttp.monad.FutureMonad
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.interceptor.reject.RejectInterceptor
import sttp.tapir.server.interpreter.{BodyListener, FilterServerEndpoints, RequestBody, ServerInterpreter, ToResponseBody}
import sttp.tapir.server.model.ServerResponse
import sttp.tapir.server.pekkohttp.PekkoModel.parseHeadersOrThrowWithoutContentHeaders

import scala.concurrent.{ExecutionContext, Future}

trait PekkoHttpServerInterpreter {

  implicit def executionContext: ExecutionContext

  def pekkoHttpServerOptions: PekkoHttpServerOptions = PekkoHttpServerOptions.default

  def toRoute(se: ServerEndpoint[PekkoStreams with WebSockets, Future]): Route = toRoute(List(se))

  def toRoute(ses: List[ServerEndpoint[PekkoStreams with WebSockets, Future]]): Route =
    toRoute(new PekkoRequestBody(pekkoHttpServerOptions)(_, _), new PekkoToResponseBody()(_, _))(ses)

  protected def toRoute(
      requestBody: (Materializer, ExecutionContext) => RequestBody[Future, PekkoStreams],
      toResponseBody: (Materializer, ExecutionContext) => ToResponseBody[PekkoResponseBody, PekkoStreams]
  )(ses: List[ServerEndpoint[PekkoStreams with WebSockets, Future]]): Route = {
    val filterServerEndpoints = FilterServerEndpoints(ses)
    val interceptors = RejectInterceptor.disableWhenSingleEndpoint(
      pekkoHttpServerOptions.appendInterceptor(PekkoStreamSizeExceptionInterceptor).interceptors,
      ses
    )

    extractExecutionContext { implicit ec =>
      extractMaterializer { implicit mat =>
        implicit val monad: FutureMonad = new FutureMonad()
        implicit val bodyListener: BodyListener[Future, PekkoResponseBody] = new PekkoBodyListener

        val interpreter = new ServerInterpreter(
          filterServerEndpoints,
          requestBody(mat, ec),
          toResponseBody(mat, ec),
          interceptors,
          pekkoHttpServerOptions.deleteFile
        )

        extractRequestContext { ctx =>
          val serverRequest = PekkoServerRequest(ctx)
          onSuccess(interpreter(serverRequest)) {
            case RequestResult.Failure(_)         => reject
            case RequestResult.Response(response) => serverResponseToPekko(response, serverRequest.method)
          }
        }
      }
    }
  }

  private def serverResponseToPekko(response: ServerResponse[PekkoResponseBody], requestMethod: Method): Route = {
    val statusCode = StatusCodes
      .getForKey(response.code.code)
      .getOrElse(StatusCodes.custom(response.code.code, "", "", false, true))
    val pekkoHeaders = parseHeadersOrThrowWithoutContentHeaders(response)

    response.body match {
      case Some(Left(flow)) =>
        respondWithHeaders(pekkoHeaders) {
          handleWebSocketMessages(flow)
        }
      case Some(Right(entity)) =>
        complete(HttpResponse(entity = entity, status = statusCode, headers = pekkoHeaders))
      case None =>
        if (requestMethod.is(Method.HEAD) && response.contentLength.isDefined) {
          val contentLength: Long = response.contentLength.getOrElse(0)
          val contentType: ContentType = response.contentType match {
            case Some(t) => ContentType.parse(t).getOrElse(ContentTypes.NoContentType)
            case None    => ContentTypes.NoContentType
          }
          complete(
            HttpResponse(
              status = statusCode,
              headers = pekkoHeaders,
              entity = HttpEntity.Default(contentType, contentLength, Source.empty)
            )
          )
        } else
          response.contentType match {
            case Some(t) =>
              val contentType = ContentType.parse(t).getOrElse(ContentTypes.NoContentType)
              complete(HttpResponse(statusCode, headers = pekkoHeaders, entity = HttpEntity.Strict(contentType, ByteString.empty)))
            case None => complete(HttpResponse(statusCode, headers = pekkoHeaders))
          }
    }
  }
}

object PekkoHttpServerInterpreter {
  def apply()(implicit _ec: ExecutionContext): PekkoHttpServerInterpreter = {
    new PekkoHttpServerInterpreter {
      override implicit def executionContext: ExecutionContext = _ec
    }
  }

  def apply(serverOptions: PekkoHttpServerOptions)(implicit _ec: ExecutionContext): PekkoHttpServerInterpreter = {
    new PekkoHttpServerInterpreter {
      override implicit def executionContext: ExecutionContext = _ec

      override def pekkoHttpServerOptions: PekkoHttpServerOptions = serverOptions
    }
  }
}
