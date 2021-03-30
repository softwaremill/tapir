package sttp.tapir.server.play

import akka.stream.Materializer
import play.api.http.{HeaderNames, HttpEntity}
import play.api.mvc._
import play.api.routing.Router.Routes
import sttp.model.StatusCode
import sttp.monad.FutureMonad
import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.DecodeFailureContext
import sttp.tapir.server.interpreter.{DecodeBasicInputs, DecodeBasicInputsResult, ServerInterpreter}

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.reflect.ClassTag

trait PlayServerInterpreter {

  def toRoutes[I, E, O](e: Endpoint[I, E, O, Any])(
      logic: I => Future[Either[E, O]]
  )(implicit mat: Materializer, serverOptions: PlayServerOptions): Routes = {
    toRoutes(e.serverLogic(logic))
  }

  def toRoutesRecoverErrors[I, E, O](e: Endpoint[I, E, O, Any])(logic: I => Future[O])(implicit
      eIsThrowable: E <:< Throwable,
      eClassTag: ClassTag[E],
      mat: Materializer,
      serverOptions: PlayServerOptions
  ): Routes = {
    toRoutes(e.serverLogicRecoverErrors(logic))
  }

  def toRoutes[I, E, O](e: ServerEndpoint[I, E, O, Any, Future])(implicit mat: Materializer, serverOptions: PlayServerOptions): Routes = {
    toRoutes(List(e))
  }

  def toRoutes[I, E, O](
      serverEndpoints: List[ServerEndpoint[_, _, _, Any, Future]]
  )(implicit mat: Materializer, serverOptions: PlayServerOptions): Routes = {
    implicit val ec: ExecutionContextExecutor = mat.executionContext
    implicit val monad: FutureMonad = new FutureMonad()

    new PartialFunction[RequestHeader, Handler] {
      override def isDefinedAt(request: RequestHeader): Boolean = {
        val serverRequest = new PlayServerRequest(request)
        serverEndpoints.exists { se =>
          DecodeBasicInputs(se.input, serverRequest) match {
            case DecodeBasicInputsResult.Values(_, _) => true
            case DecodeBasicInputsResult.Failure(input, failure) =>
              serverOptions.decodeFailureHandler(DecodeFailureContext(input, failure, se.endpoint, serverRequest)).isDefined
          }
        }
      }

      override def apply(v1: RequestHeader): Handler = {
        serverOptions.defaultActionBuilder.async(serverOptions.playBodyParsers.raw) { request =>
          val serverRequest = new PlayServerRequest(request)
          val interpreter = new ServerInterpreter[Any, Future, HttpEntity, Nothing](
            new PlayRequestBody(request, serverOptions),
            new PlayToResponseBody,
            serverOptions.interceptors
          )

          interpreter(serverRequest, serverEndpoints).map {
            case None => Result(header = ResponseHeader(StatusCode.NotFound.code), body = HttpEntity.NoEntity)
            case Some(response) =>
              val headers: Map[String, String] = response.headers
                .foldLeft(Map.empty[String, List[String]]) { (a, b) =>
                  if (a.contains(b.name)) a + (b.name -> (a(b.name) :+ b.value)) else a + (b.name -> List(b.value))
                }
                .map {
                  // See comment in play.api.mvc.CookieHeaderEncoding
                  case (key, value) if key == HeaderNames.SET_COOKIE => (key, value.mkString(";;"))
                  case (key, value)                                  => (key, value.mkString(", "))
                }
              val status = response.code.code

              response.body match {
                case Some(entity) =>
                  val result = Result(ResponseHeader(status, headers), entity)
                  headers.find(_._1.toLowerCase == "content-type").map(ct => result.as(ct._2)).getOrElse(result)
                case None => Result(ResponseHeader(status, headers), HttpEntity.NoEntity)
              }
          }
        }
      }
    }
  }
}

object PlayServerInterpreter extends PlayServerInterpreter
