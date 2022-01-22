package sttp.tapir.server.play

import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Source}
import akka.util.ByteString
import play.api.http.websocket.Message
import play.api.http.{HeaderNames, HttpEntity}
import play.api.libs.streams.Accumulator
import play.api.mvc._
import play.api.routing.Router.Routes
import sttp.capabilities.WebSockets
import sttp.capabilities.akka.AkkaStreams
import sttp.model.{Method, StatusCode}
import sttp.monad.FutureMonad
import sttp.tapir.model.ServerResponse
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.{DecodeFailureContext, RequestResult}
import sttp.tapir.server.interpreter.{BodyListener, DecodeBasicInputs, DecodeBasicInputsResult, DecodeInputsContext, ServerInterpreter}

import scala.concurrent.{ExecutionContextExecutor, Future}

trait PlayServerInterpreter {

  implicit def mat: Materializer

  implicit def executionContext: ExecutionContextExecutor = mat.executionContext

  def playServerOptions: PlayServerOptions = PlayServerOptions.default

  private val streamParser: BodyParser[AkkaStreams.BinaryStream] = BodyParser { _ =>
    Accumulator.source[ByteString].map(Right.apply)
  }

  def toRoutes(e: ServerEndpoint[AkkaStreams with WebSockets, Future]): Routes = {
    toRoutes(List(e))
  }

  def toRoutes[I, E, O](
      serverEndpoints: List[ServerEndpoint[AkkaStreams with WebSockets, Future]]
  ): Routes = {
    implicit val monad: FutureMonad = new FutureMonad()

    new PartialFunction[RequestHeader, Handler] {
      override def isDefinedAt(request: RequestHeader): Boolean = {
        val serverRequest = new PlayServerRequest(request, request)
        serverEndpoints.exists { se =>
          DecodeBasicInputs(se.securityInput.and(se.input), DecodeInputsContext(serverRequest), matchWholePath = true) match {
            case (DecodeBasicInputsResult.Values(_, _), _) => true
            case (DecodeBasicInputsResult.Failure(input, failure), _) =>
              playServerOptions.decodeFailureHandler(DecodeFailureContext(input, failure, se.endpoint, serverRequest)).isDefined
          }
        }
      }

      override def apply(header: RequestHeader): Handler =
        if (isWebSocket(header))
          WebSocket.acceptOrResult { header =>
            getResponse(header, header.withBody(Source.empty))
          }
        else
          playServerOptions.defaultActionBuilder.async(streamParser) { request =>
            getResponse(header, request).flatMap {
              case Left(result) => Future.successful(result)
              case Right(_)     => Future.failed(new Exception("Only WebSocket requests accept flows."))
            }
          }

      private def getResponse(
          header: RequestHeader,
          request: Request[AkkaStreams.BinaryStream]
      ): Future[Either[Result, Flow[Message, Message, Any]]] = {
        implicit val bodyListener: BodyListener[Future, PlayResponseBody] = new PlayBodyListener
        val serverRequest = new PlayServerRequest(header, request)
        val interpreter = new ServerInterpreter(
          serverEndpoints,
          new PlayToResponseBody,
          playServerOptions.interceptors,
          playServerOptions.deleteFile
        )

        interpreter(serverRequest, new PlayRequestBody(request, playServerOptions)).map {
          case RequestResult.Failure(_) =>
            Left(Result(header = ResponseHeader(StatusCode.NotFound.code), body = HttpEntity.NoEntity))
          case RequestResult.Response(response: ServerResponse[PlayResponseBody]) =>
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
              case Some(Left(flow))    => Right(flow)
              case Some(Right(entity)) => Left(Result(ResponseHeader(status, headers), entity))
              case None =>
                if (serverRequest.method.is(Method.HEAD) && response.contentLength.isDefined)
                  Left(
                    Result(ResponseHeader(status, headers), HttpEntity.Streamed(Source.empty, response.contentLength, response.contentType))
                  )
                else Left(Result(ResponseHeader(status, headers), HttpEntity.NoEntity))
            }
        }
      }
    }
  }

  private def isWebSocket(header: RequestHeader): Boolean =
    (for {
      connection <- header.headers.get(sttp.model.HeaderNames.Connection)
      upgrade <- header.headers.get(sttp.model.HeaderNames.Upgrade)
    } yield connection.equalsIgnoreCase("Upgrade") && upgrade.equalsIgnoreCase("websocket")).getOrElse(false)

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
