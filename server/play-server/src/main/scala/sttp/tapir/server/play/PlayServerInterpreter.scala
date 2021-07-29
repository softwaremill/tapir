package sttp.tapir.server.play

import akka.stream.Materializer
import play.api.http.{HeaderNames, HttpEntity}
import play.api.mvc._
import play.api.routing.Router.Routes
import sttp.model.StatusCode
import sttp.monad.FutureMonad
import sttp.tapir.Endpoint
import sttp.tapir.internal.NoStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.DecodeFailureContext
import sttp.tapir.server.interpreter.{BodyListener, DecodeBasicInputs, DecodeBasicInputsResult, ServerInterpreter}

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.reflect.ClassTag

trait PlayServerInterpreter {

  implicit def mat: Materializer

  implicit def executionContext: ExecutionContextExecutor = mat.executionContext

  def playServerOptions: PlayServerOptions = PlayServerOptions.default

  def toRoutes[I, E, O](e: Endpoint[I, E, O, Any])(
      logic: I => Future[Either[E, O]]
  ): Routes = {
    toRoutes(e.serverLogic(logic))
  }

  def toRoutesRecoverErrors[I, E, O](e: Endpoint[I, E, O, Any])(logic: I => Future[O])(implicit
      eIsThrowable: E <:< Throwable,
      eClassTag: ClassTag[E]
  ): Routes = {
    toRoutes(e.serverLogicRecoverErrors(logic))
  }

  def toRoutes[I, E, O](e: ServerEndpoint[I, E, O, Any, Future]): Routes = {
    toRoutes(List(e))
  }

  def toRoutes[I, E, O](
      serverEndpoints: List[ServerEndpoint[_, _, _, Any, Future]]
  ): Routes = {
    implicit val monad: FutureMonad = new FutureMonad()

    new PartialFunction[RequestHeader, Handler] {
      override def isDefinedAt(request: RequestHeader): Boolean = {
        val serverRequest = new PlayServerRequest(request)
        serverEndpoints.exists { se =>
          DecodeBasicInputs(se.input, serverRequest) match {
            case DecodeBasicInputsResult.Values(_, _) => true
            case DecodeBasicInputsResult.Failure(input, failure) =>
              playServerOptions.decodeFailureHandler(DecodeFailureContext(input, failure, se.endpoint, serverRequest)).isDefined
          }
        }
      }

      override def apply(header: RequestHeader): Handler = {
        playServerOptions.defaultActionBuilder.async(playServerOptions.playBodyParsers.raw) { request =>
          implicit val bodyListener: BodyListener[Future, HttpEntity] = new PlayBodyListener
          val serverRequest = new PlayServerRequest(header)
          val interpreter = new ServerInterpreter[Any, Future, HttpEntity, NoStreams](
            new PlayRequestBody(request, playServerOptions),
            new PlayToResponseBody,
            playServerOptions.interceptors,
            playServerOptions.deleteFile
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

object PlayServerInterpreter {
  def apply()(implicit _mat: Materializer): PlayServerInterpreter = {
    new PlayServerInterpreter {
      override implicit def mat: Materializer = _mat
    }
  }

  def apply(serverOptions: PlayServerOptions)(implicit _mat: Materializer): PlayServerInterpreter = {
    new PlayServerInterpreter {
      override implicit def mat: Materializer = _mat

      override def playServerOptions: PlayServerOptions = serverOptions
    }
  }
}
