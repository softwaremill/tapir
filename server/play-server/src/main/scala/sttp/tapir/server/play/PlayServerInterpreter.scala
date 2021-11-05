package sttp.tapir.server.play

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import play.api.http.{HeaderNames, HttpEntity}
import play.api.libs.streams.Accumulator
import play.api.mvc._
import play.api.routing.Router.Routes
import sttp.capabilities.akka.AkkaStreams
import sttp.model.{Method, StatusCode}
import sttp.monad.FutureMonad
import sttp.tapir.model.ServerResponse
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.{DecodeFailureContext, RequestResult}
import sttp.tapir.server.interpreter.{BodyListener, DecodeBasicInputs, DecodeBasicInputsResult, ServerInterpreter}

import scala.concurrent.{ExecutionContextExecutor, Future}

trait PlayServerInterpreter {

  implicit def mat: Materializer

  implicit def executionContext: ExecutionContextExecutor = mat.executionContext

  def playServerOptions: PlayServerOptions = PlayServerOptions.default

  private val streamParser: BodyParser[AkkaStreams.BinaryStream] = BodyParser { _ =>
    Accumulator.source[ByteString].map(Right.apply)
  }

  def toRoutes(e: ServerEndpoint[AkkaStreams, Future]): Routes = {
    toRoutes(List(e))
  }

  def toRoutes[I, E, O](
      serverEndpoints: List[ServerEndpoint[AkkaStreams, Future]]
  ): Routes = {
    implicit val monad: FutureMonad = new FutureMonad()

    new PartialFunction[RequestHeader, Handler] {
      override def isDefinedAt(request: RequestHeader): Boolean = {
        val serverRequest = new PlayServerRequest(request, request)
        serverEndpoints.exists { se =>
          DecodeBasicInputs(se.input, serverRequest) match {
            case DecodeBasicInputsResult.Values(_, _) => true
            case DecodeBasicInputsResult.Failure(input, failure) =>
              playServerOptions.decodeFailureHandler(DecodeFailureContext(input, failure, se.endpoint, serverRequest)).isDefined
          }
        }
      }

      override def apply(header: RequestHeader): Handler = {
        playServerOptions.defaultActionBuilder.async(streamParser) { request =>
          implicit val bodyListener: BodyListener[Future, HttpEntity] = new PlayBodyListener
          val serverRequest = new PlayServerRequest(header, request)
          val interpreter = new ServerInterpreter(
            new PlayRequestBody(request, playServerOptions),
            new PlayToResponseBody,
            playServerOptions.interceptors,
            playServerOptions.deleteFile
          )

          interpreter(serverRequest, serverEndpoints).map {
            case RequestResult.Failure(_) =>
              Result(header = ResponseHeader(StatusCode.NotFound.code), body = HttpEntity.NoEntity)
            case RequestResult.Response(response: ServerResponse[HttpEntity]) =>
              val headers: Map[String, String] = response.headers
                .foldLeft(Map.empty[String, List[String]]) { (a, b) =>
                  if (a.contains(b.name)) a + (b.name -> (a(b.name) :+ b.value)) else a + (b.name -> List(b.value))
                }
                .map {
                  // See comment in play.api.mvc.CookieHeaderEncoding
                  case (key, value) if key == HeaderNames.SET_COOKIE => (key, value.mkString(";;"))
                  case (key, value)                                  => (key, value.mkString(", "))
                }
                .filterNot(allowToSetExplicitly)

              val status = response.code.code
              response.body match {
                case Some(entity) => Result(ResponseHeader(status, headers), entity)
                case None =>
                  if (serverRequest.method.is(Method.HEAD) && response.contentLength.isDefined)
                    Result(ResponseHeader(status, headers), HttpEntity.Streamed(Source.empty, response.contentLength, response.contentType))
                  else Result(ResponseHeader(status, headers), HttpEntity.NoEntity)
              }
          }
        }
      }
    }
  }

  private def allowToSetExplicitly[O, E, I](header: (String, String)): Boolean =
    List(HeaderNames.CONTENT_TYPE, HeaderNames.CONTENT_LENGTH, HeaderNames.TRANSFER_ENCODING).contains(header._1)

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
