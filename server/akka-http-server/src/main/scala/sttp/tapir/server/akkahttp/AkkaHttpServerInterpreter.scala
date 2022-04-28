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
import sttp.tapir.server.interpreter.{BodyListener, FilterServerEndpoints, ServerInterpreter}
import sttp.tapir.server.model.ServerResponse

import scala.concurrent.{ExecutionContext, Future}

trait AkkaHttpServerInterpreter {

  def akkaHttpServerOptions: AkkaHttpServerOptions

  def toRoute(se: ServerEndpoint[AkkaStreams with WebSockets, Future]): Route = toRoute(List(se))

  def toRoute(ses: List[ServerEndpoint[AkkaStreams with WebSockets, Future]]): Route = {
    val filterServerEndpoints = FilterServerEndpoints(ses)
    val interceptors = RejectInterceptor.disableWhenSingleEndpoint(akkaHttpServerOptions.interceptors, ses)

    extractExecutionContext { implicit ec =>
      extractMaterializer { implicit mat =>
        implicit val monad: FutureMonad = new FutureMonad()
        implicit val bodyListener: BodyListener[Future, AkkaResponseBody] = new AkkaBodyListener

        val interpreter = new ServerInterpreter(
          filterServerEndpoints,
          new AkkaRequestBody(akkaHttpServerOptions),
          new AkkaToResponseBody,
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

  def apply()(implicit ec: ExecutionContext): AkkaHttpServerInterpreter = apply(AkkaHttpServerOptions.default)
  def apply(serverOptions: AkkaHttpServerOptions): AkkaHttpServerInterpreter = {
    new AkkaHttpServerInterpreter {
      override def akkaHttpServerOptions: AkkaHttpServerOptions = serverOptions
    }
  }
}
