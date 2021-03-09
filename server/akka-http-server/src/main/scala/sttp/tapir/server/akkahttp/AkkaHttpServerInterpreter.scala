package sttp.tapir.server.akkahttp

import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.model.{HttpResponse, ResponseEntity, StatusCode => AkkaStatusCode}
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
import akka.stream.scaladsl.Flow
import sttp.capabilities.WebSockets
import sttp.capabilities.akka.AkkaStreams
import sttp.monad.FutureMonad
import sttp.tapir.Endpoint
import sttp.tapir.model.ServerResponse
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.akkahttp.AkkaModel.parseHeadersOrThrowWithoutContentType
import sttp.tapir.server.internal.ServerInterpreter

import scala.concurrent.Future
import scala.reflect.ClassTag

trait AkkaHttpServerInterpreter {
  def toRoute[I, E, O](e: Endpoint[I, E, O, AkkaStreams with WebSockets])(logic: I => Future[Either[E, O]])(implicit
      serverOptions: AkkaHttpServerOptions
  ): Route = toRoute(e.serverLogic(logic))

  def toRouteRecoverErrors[I, E, O](
      e: Endpoint[I, E, O, AkkaStreams with WebSockets]
  )(logic: I => Future[O])(implicit eIsThrowable: E <:< Throwable, eClassTag: ClassTag[E], serverOptions: AkkaHttpServerOptions): Route =
    toRoute(e.serverLogicRecoverErrors(logic))

  def toRoute[I, E, O](se: ServerEndpoint[I, E, O, AkkaStreams with WebSockets, Future])(implicit
      serverOptions: AkkaHttpServerOptions
  ): Route = toRoute(List(se))

  def toRoute(ses: List[ServerEndpoint[_, _, _, AkkaStreams with WebSockets, Future]])(implicit
      serverOptions: AkkaHttpServerOptions
  ): Route = {
    extractRequestContext { ctx =>
      extractExecutionContext { implicit ec =>
        extractMaterializer { implicit mat =>
          implicit val monad: FutureMonad = new FutureMonad()
          val serverRequest = new AkkaServerRequest(ctx)
          val interpreter = new ServerInterpreter(
            serverRequest,
            new AkkaRequestBody(ctx, serverRequest, serverOptions),
            new AkkaToResponseBody,
            serverOptions.interceptors
          )

          onSuccess(interpreter(ses)) {
            case None           => reject
            case Some(response) => serverResponseToAkka(response)
          }
        }
      }
    }
  }

  private def serverResponseToAkka(response: ServerResponse[Flow[Message, Message, Any], ResponseEntity]): Route = {
    val statusCode = AkkaStatusCode.int2StatusCode(response.code.code)
    val akkaHeaders = parseHeadersOrThrowWithoutContentType(response)

    response.body match {
      case Some(Left(flow)) =>
        respondWithHeaders(akkaHeaders) {
          handleWebSocketMessages(flow)
        }
      case Some(Right(entity)) =>
        complete(HttpResponse(entity = entity, status = statusCode, headers = akkaHeaders))
      case None => complete(HttpResponse(statusCode, headers = akkaHeaders))
    }
  }
}

object AkkaHttpServerInterpreter extends AkkaHttpServerInterpreter
