package sttp.tapir.server.akkahttp

import akka.http.scaladsl.model.{HttpMethod, HttpMethods, HttpResponse, StatusCodes}
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
import akka.http.scaladsl.server.MethodRejection
import akka.http.scaladsl.server.Route
import sttp.capabilities.WebSockets
import sttp.capabilities.akka.AkkaStreams
import sttp.model.Method
import sttp.monad.FutureMonad
import sttp.tapir.Endpoint
import sttp.tapir.EndpointInput
import sttp.tapir.model.ServerResponse
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.akkahttp.AkkaModel.parseHeadersOrThrowWithoutContentHeaders
import sttp.tapir.server.interceptor.ServerInterpreterResult
import sttp.tapir.server.interpreter.{BodyListener, DecodeBasicInputsResult, ServerInterpreter}

import scala.concurrent.Future
import scala.reflect.ClassTag

trait AkkaHttpServerInterpreter {

  def akkaHttpServerOptions: AkkaHttpServerOptions = AkkaHttpServerOptions.default

  def toRoute[I, E, O](e: Endpoint[I, E, O, AkkaStreams with WebSockets])(logic: I => Future[Either[E, O]]): Route =
    toRoute(e.serverLogic(logic))

  def toRouteRecoverErrors[I, E, O](
      e: Endpoint[I, E, O, AkkaStreams with WebSockets]
  )(logic: I => Future[O])(implicit eIsThrowable: E <:< Throwable, eClassTag: ClassTag[E]): Route =
    toRoute(e.serverLogicRecoverErrors(logic))

  def toRoute[I, E, O](se: ServerEndpoint[I, E, O, AkkaStreams with WebSockets, Future]): Route = toRoute(List(se))

  def toRoute(ses: List[ServerEndpoint[_, _, _, AkkaStreams with WebSockets, Future]]): Route = {
    extractRequestContext { ctx =>
      extractExecutionContext { implicit ec =>
        extractMaterializer { implicit mat =>
          implicit val monad: FutureMonad = new FutureMonad()
          implicit val bodyListener: BodyListener[Future, AkkaResponseBody] = new AkkaBodyListener
          val serverRequest = new AkkaServerRequest(ctx)
          val interpreter = new ServerInterpreter(
            new AkkaRequestBody(ctx, serverRequest, akkaHttpServerOptions),
            new AkkaToResponseBody,
            akkaHttpServerOptions.interceptors,
            akkaHttpServerOptions.deleteFile
          )

          onSuccess(interpreter(serverRequest, ses)) {
            case ServerInterpreterResult.Failure(decodeFailureContexts) =>
              val rejections = decodeFailureContexts.map { case (_, failure) => failure }.collect {
                case DecodeBasicInputsResult.Failure(EndpointInput.FixedMethod(m, _, _), _) =>
                  MethodRejection(methodToAkkaHttp(m))
              }
              reject(rejections.toSeq: _*)
            case ServerInterpreterResult.Success(response) =>
              serverResponseToAkka(response)
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

  private def methodToAkkaHttp(m: Method): HttpMethod = m match {
    case Method.CONNECT => HttpMethods.CONNECT
    case Method.DELETE  => HttpMethods.DELETE
    case Method.GET     => HttpMethods.GET
    case Method.HEAD    => HttpMethods.HEAD
    case Method.OPTIONS => HttpMethods.OPTIONS
    case Method.PATCH   => HttpMethods.PATCH
    case Method.POST    => HttpMethods.POST
    case Method.PUT     => HttpMethods.PUT
    case Method.TRACE   => HttpMethods.TRACE
    case Method(s)      => HttpMethod.custom(s)
  }
}

object AkkaHttpServerInterpreter {

  def apply(serverOptions: AkkaHttpServerOptions = AkkaHttpServerOptions.default): AkkaHttpServerInterpreter = {
    new AkkaHttpServerInterpreter {
      override def akkaHttpServerOptions: AkkaHttpServerOptions = serverOptions
    }
  }
}
