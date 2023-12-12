package sttp.tapir.server.akkahttp

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{
  complete,
  extractExecutionContext,
  extractMaterializer,
  extractRequestContext,
  handleWebSocketMessages,
  onSuccess,
  reject,
  respondWithHeaders
}
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import sttp.capabilities.WebSockets
import sttp.capabilities.akka.AkkaStreams
import sttp.model.Method
import sttp.monad.FutureMonad
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.akkahttp.AkkaModel.parseHeadersOrThrowWithoutContentHeaders
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.interceptor.reject.RejectInterceptor
import sttp.tapir.server.interpreter.{BodyListener, FilterServerEndpoints, RequestBody, ServerInterpreter, ToResponseBody}
import sttp.tapir.server.model.ServerResponse

import scala.concurrent.{ExecutionContext, Future}

trait AkkaHttpServerInterpreter {

  implicit def executionContext: ExecutionContext

  def akkaHttpServerOptions: AkkaHttpServerOptions = AkkaHttpServerOptions.default

  def toRoute(se: ServerEndpoint[AkkaStreams with WebSockets, Future]): Route = toRoute(List(se))

  def toRoute(ses: List[ServerEndpoint[AkkaStreams with WebSockets, Future]]): Route =
    toRoute(new AkkaRequestBody(akkaHttpServerOptions)(_, _), new AkkaToResponseBody()(_, _))(ses)

  protected def toRoute(
      requestBody: (Materializer, ExecutionContext) => RequestBody[Future, AkkaStreams],
      toResponseBody: (Materializer, ExecutionContext) => ToResponseBody[AkkaResponseBody, AkkaStreams]
  )(ses: List[ServerEndpoint[AkkaStreams with WebSockets, Future]]): Route = {
    val filterServerEndpoints = FilterServerEndpoints(ses)
    val interceptors = RejectInterceptor.disableWhenSingleEndpoint(
      akkaHttpServerOptions.appendInterceptor(AkkaStreamSizeExceptionInterceptor).interceptors,
      ses
    )

    extractExecutionContext { implicit ec =>
      extractMaterializer { implicit mat =>
        implicit val monad: FutureMonad = new FutureMonad()
        implicit val bodyListener: BodyListener[Future, AkkaResponseBody] = new AkkaBodyListener

        val interpreter = new ServerInterpreter(
          filterServerEndpoints,
          requestBody(mat, ec),
          toResponseBody(mat, ec),
          interceptors,
          akkaHttpServerOptions.deleteFile
        )

        extractRequestContext { ctx =>
          val serverRequest = AkkaServerRequest(ctx)
          onSuccess(interpreter(serverRequest)) {
            case RequestResult.Failure(_)         => reject
            case RequestResult.Response(response) => serverResponseToAkka(response, serverRequest.method)
          }
        }
      }
    }
  }

  private def serverResponseToAkka(response: ServerResponse[AkkaResponseBody], requestMethod: Method): Route = {
    val statusCode = StatusCodes.getForKey(response.code.code).getOrElse(StatusCodes.custom(response.code.code, ""))
    val akkaHeaders = parseHeadersOrThrowWithoutContentHeaders(response)

    response.body match {
      case Some(Left(flow)) =>
        respondWithHeaders(akkaHeaders) {
          handleWebSocketMessages(flow)
        }
      case Some(Right(entity)) =>
        complete(HttpResponse(entity = entity, status = statusCode, headers = akkaHeaders))
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
              headers = akkaHeaders,
              entity = HttpEntity.Default(contentType, contentLength, Source.empty)
            )
          )
        } else
          response.contentType match {
            case Some(t) =>
              val contentType = ContentType.parse(t).getOrElse(ContentTypes.NoContentType)
              complete(HttpResponse(statusCode, headers = akkaHeaders, entity = HttpEntity.Strict(contentType, ByteString.empty)))
            case None => complete(HttpResponse(statusCode, headers = akkaHeaders))
          }
    }
  }
}

object AkkaHttpServerInterpreter {
  def apply()(implicit _ec: ExecutionContext): AkkaHttpServerInterpreter = {
    new AkkaHttpServerInterpreter {
      override implicit def executionContext: ExecutionContext = _ec
    }
  }

  def apply(serverOptions: AkkaHttpServerOptions)(implicit _ec: ExecutionContext): AkkaHttpServerInterpreter = {
    new AkkaHttpServerInterpreter {
      override implicit def executionContext: ExecutionContext = _ec

      override def akkaHttpServerOptions: AkkaHttpServerOptions = serverOptions
    }
  }
}
