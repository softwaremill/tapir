package sttp.tapir.server.akkahttp

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
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
import sttp.capabilities.WebSockets
import sttp.capabilities.akka.AkkaStreams
import sttp.monad.FutureMonad
import sttp.tapir.Endpoint
import sttp.tapir.model.ServerResponse
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.akkahttp.AkkaModel.parseHeadersOrThrowWithoutContentHeaders
import sttp.tapir.server.interpreter.{BodyListener, ServerInterpreter}

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
          implicit val bodyListener: BodyListener[Future, AkkaResponseBody] = new AkkaBodyListener
          val serverRequest = new AkkaServerRequest(ctx)
          val interpreter = new ServerInterpreter(
            new AkkaRequestBody(ctx, serverRequest, serverOptions),
            new AkkaToResponseBody,
            serverOptions.interceptors,
            serverOptions.deleteFile
          )

          onSuccess(interpreter(serverRequest, ses)) {
            case None           => reject
            case Some(response) => serverResponseToAkka(response)
          }
        }
      }
    }
  }

  private def serverResponseToAkka(response: ServerResponse[AkkaResponseBody]): Route = {
    val statusCode = StatusCodes.getForKey(response.code.code).getOrElse(StatusCodes.custom(response.code.code, ""))
    val akkaHeaders = parseHeadersOrThrowWithoutContentHeaders(response)

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
