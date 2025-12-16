package sttp.tapir.server.http4s

import cats.data.{Kleisli, OptionT}
import cats.effect.Async
import cats.implicits._
import org.http4s._
import org.http4s.headers.`Content-Length`
import org.http4s.server.websocket.WebSocketBuilder2
import org.typelevel.ci.CIString
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir._
import sttp.tapir.integ.cats.effect.CatsMonadError
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.interceptor.reject.RejectInterceptor
import sttp.tapir.server.interpreter.{BodyListener, FilterServerEndpoints, ServerInterpreter}
import sttp.tapir.server.model.ServerResponse

import scala.reflect.ClassTag

class Http4sInvalidWebSocketUse(val message: String) extends Exception

/** A capability that is used by endpoints, when they need to access the http4s-provided context. Such a requirement can be added using the
  * [[RichHttp4sEndpoint.contextIn]] or [[RichHttp4sEndpoint.contextSecurityIn]] methods.
  */
trait Context[T]

trait Http4sServerInterpreter[F[_]] {
  implicit def fa: Async[F]

  def http4sServerOptions: Http4sServerOptions[F] = Http4sServerOptions.default[F]

  //

  def toRoutes(se: ServerEndpoint[Fs2Streams[F], F]): HttpRoutes[F] =
    toRoutes(List(se))

  def toRoutes(serverEndpoints: List[ServerEndpoint[Fs2Streams[F], F]]): HttpRoutes[F] =
    toRoutes(serverEndpoints, None)

  def toWebSocketRoutes(se: ServerEndpoint[Fs2Streams[F] with WebSockets, F]): WebSocketBuilder2[F] => HttpRoutes[F] =
    toWebSocketRoutes(List(se))

  def toWebSocketRoutes(
      serverEndpoints: List[ServerEndpoint[Fs2Streams[F] with WebSockets, F]]
  ): WebSocketBuilder2[F] => HttpRoutes[F] = wsb => toRoutes(serverEndpoints, Some(wsb))

  def toContextRoutes[T: ClassTag](se: ServerEndpoint[Fs2Streams[F] with Context[T], F]): ContextRoutes[T, F] =
    toContextRoutes(contextAttributeKey[T], List(se), None)

  def toContextRoutes[T: ClassTag](ses: List[ServerEndpoint[Fs2Streams[F] with Context[T], F]]): ContextRoutes[T, F] =
    toContextRoutes(contextAttributeKey[T], ses, None)

  def toContextWebSocketRoutes[T: ClassTag](
      se: ServerEndpoint[Fs2Streams[F] with Context[T] with WebSockets, F]
  ): WebSocketBuilder2[F] => ContextRoutes[T, F] =
    wsb => toContextRoutes(contextAttributeKey[T], List(se), Some(wsb))

  def toContextWebSocketRoutes[T: ClassTag](
      ses: List[ServerEndpoint[Fs2Streams[F] with Context[T] with WebSockets, F]]
  ): WebSocketBuilder2[F] => ContextRoutes[T, F] =
    wsb => toContextRoutes(contextAttributeKey[T], ses, Some(wsb))

  private def createInterpreter[T](
      serverEndpoints: List[ServerEndpoint[Fs2Streams[F] with WebSockets with Context[T], F]]
  ): ServerInterpreter[Fs2Streams[F] with WebSockets with Context[T], F, Http4sResponseBody[F], Fs2Streams[F]] = {
    implicit val monad: CatsMonadError[F] = new CatsMonadError[F]
    implicit val bodyListener: BodyListener[F, Http4sResponseBody[F]] = new Http4sBodyListener[F]

    new ServerInterpreter(
      FilterServerEndpoints(serverEndpoints),
      new Http4sRequestBody[F](http4sServerOptions),
      new Http4sToResponseBody[F](http4sServerOptions),
      RejectInterceptor.disableWhenSingleEndpoint(http4sServerOptions.interceptors, serverEndpoints),
      http4sServerOptions.deleteFile
    )
  }

  private def toResponse[T](
      interpreter: ServerInterpreter[Fs2Streams[F] with WebSockets with Context[T], F, Http4sResponseBody[F], Fs2Streams[F]],
      serverRequest: Http4sServerRequest[F],
      webSocketBuilder: Option[WebSocketBuilder2[F]]
  ): OptionT[F, Response[F]] =
    OptionT(interpreter(serverRequest).flatMap {
      case _: RequestResult.Failure            => none.pure[F]
      case RequestResult.Response(response, _) => serverResponseToHttp4s(response, webSocketBuilder).map(_.some)
    })

  private def toRoutes(
      serverEndpoints: List[ServerEndpoint[Fs2Streams[F] with WebSockets, F]],
      webSocketBuilder: Option[WebSocketBuilder2[F]]
  ): HttpRoutes[F] = {
    val interpreter = createInterpreter(serverEndpoints)

    Kleisli { (req: Request[F]) =>
      val serverRequest = Http4sServerRequest(req)
      toResponse(interpreter, serverRequest, webSocketBuilder)
    }
  }

  private def toContextRoutes[T](
      contextAttributeKey: AttributeKey[T],
      serverEndpoints: List[ServerEndpoint[Fs2Streams[F] with WebSockets with Context[T], F]],
      webSocketBuilder: Option[WebSocketBuilder2[F]]
  ): ContextRoutes[T, F] = {
    val interpreter = createInterpreter(serverEndpoints)

    Kleisli { (contextRequest: ContextRequest[F, T]) =>
      val serverRequest = Http4sServerRequest(
        contextRequest.req,
        AttributeMap.Empty.put(contextAttributeKey, contextRequest.context)
      )
      toResponse(interpreter, serverRequest, webSocketBuilder)
    }
  }

  private def serverResponseToHttp4s(
      response: ServerResponse[Http4sResponseBody[F]],
      webSocketBuilder: Option[WebSocketBuilder2[F]]
  ): F[Response[F]] = {
    implicit val monad: CatsMonadError[F] = new CatsMonadError[F]

    val statusCode = statusCodeToHttp4sStatus(response.code)
    val headers = Headers(response.headers.map(header => Header.Raw(CIString(header.name), header.value)).toList)

    response.body match {
      case Some(Left(pipeF)) =>
        pipeF.flatMap { pipe =>
          webSocketBuilder match {
            case Some(wsb) => wsb.withHeaders(headers).build(pipe)
            case None      =>
              monad.error(
                new Http4sInvalidWebSocketUse(
                  "Invalid usage of web socket endpoint without WebSocketBuilder2. " +
                    "Use the toWebSocketRoutes/toWebSocketHttp interpreter methods, " +
                    "and add the result using BlazeServerBuilder.withHttpWebSocketApp(..)."
                )
              )
          }
        }
      case Some(Right((entity, contentLength))) =>
        val headers2 = contentLength match {
          case Some(value) if response.contentLength.isEmpty => headers.put(`Content-Length`(value))
          case _                                             => headers
        }
        Response(status = statusCode, headers = headers2, body = entity).pure[F]

      case None => Response[F](status = statusCode, headers = headers).pure[F]
    }
  }

  private def statusCodeToHttp4sStatus(code: sttp.model.StatusCode): Status =
    Status.fromInt(code.code).getOrElse(throw new IllegalArgumentException(s"Invalid status code: $code"))
}

object Http4sServerInterpreter {

  def apply[F[_]]()(implicit _fa: Async[F]): Http4sServerInterpreter[F] = {
    new Http4sServerInterpreter[F] {
      override implicit def fa: Async[F] = _fa
    }
  }

  def apply[F[_]](serverOptions: Http4sServerOptions[F])(implicit _fa: Async[F]): Http4sServerInterpreter[F] = {
    new Http4sServerInterpreter[F] {
      override implicit def fa: Async[F] = _fa
      override def http4sServerOptions: Http4sServerOptions[F] = serverOptions
    }
  }
}
