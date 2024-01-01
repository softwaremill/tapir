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
import sttp.model.Method
import sttp.monad.FutureMonad
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.interpreter.{BodyListener, FilterServerEndpoints, ServerInterpreter}
import sttp.tapir.server.model.ServerResponse

import scala.concurrent.{ExecutionContext, Future}

trait PlayServerInterpreter {

  implicit def mat: Materializer

  implicit def executionContext: ExecutionContext = mat.executionContext

  def playServerOptions: PlayServerOptions = PlayServerOptions.default

  private val streamParser: BodyParser[AkkaStreams.BinaryStream] = BodyParser { _ =>
    Accumulator.source[ByteString].map(Right.apply)
  }

  def toRoutes(e: ServerEndpoint[AkkaStreams with WebSockets, Future]): Routes = {
    toRoutes(List(e))
  }

  def toRoutes(
      serverEndpoints: List[ServerEndpoint[AkkaStreams with WebSockets, Future]]
  ): Routes = {
    implicit val monad: FutureMonad = new FutureMonad()

    val filterServerEndpoints = FilterServerEndpoints(serverEndpoints)
    val singleEndpoint = serverEndpoints.size == 1

    new PartialFunction[RequestHeader, Handler] {
      override def isDefinedAt(request: RequestHeader): Boolean = {
        val filtered = filterServerEndpoints(PlayServerRequest(request, request))
        if (singleEndpoint) {
          // If we are interpreting a single endpoint, we verify that the method matches as well; in case it doesn't,
          // we refuse to handle the request, allowing other Play routes to handle it. Otherwise even if the method
          // doesn't match, this will be handled by the RejectInterceptor
          filtered.exists { e =>
            val m = e.endpoint.method
            m.isEmpty || m.contains(Method(request.method))
          }
        } else {
          filtered.nonEmpty
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
        val serverRequest = PlayServerRequest(header, request)
        val interpreter = new ServerInterpreter(
          filterServerEndpoints,
          new PlayRequestBody(playServerOptions),
          new PlayToResponseBody,
          playServerOptions.interceptors,
          playServerOptions.deleteFile
        )

        interpreter(serverRequest)
          .map {
            case RequestResult.Failure(_) =>
              throw new RuntimeException(
                s"The path: ${request.path} matches the shape of some endpoint, but none of the " +
                  s"endpoints decoded the request successfully, and the decode failure handler didn't provide a " +
                  s"response. Play requires that if the path shape matches some endpoints, the request " +
                  s"should be handled by tapir."
              )
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
                      Result(
                        ResponseHeader(status, headers),
                        HttpEntity.Streamed(Source.empty, response.contentLength, response.contentType)
                      )
                    )
                  else Left(Result(ResponseHeader(status, headers), HttpEntity.Strict(ByteString.empty, response.contentType)))
              }
          }
          .recover { case e: PlayBodyParserException =>
            Left(e.result)
          }
      }
    }
  }

  private def isWebSocket(header: RequestHeader): Boolean =
    (for {
      connection <- header.headers.get(sttp.model.HeaderNames.Connection)
      upgrade <- header.headers.get(sttp.model.HeaderNames.Upgrade)
    } yield connection.equalsIgnoreCase("Upgrade") && upgrade.equalsIgnoreCase("websocket")).getOrElse(false)

  private def allowToSetExplicitly(header: (String, String)): Boolean =
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
