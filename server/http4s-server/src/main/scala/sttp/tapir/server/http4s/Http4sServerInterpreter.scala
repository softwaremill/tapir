package sttp.tapir.server.http4s

import cats.data.{Kleisli, OptionT}
import cats.effect.Async
import cats.effect.std.Queue
import cats.implicits._
import fs2.{Pipe, Stream}
import org.http4s._
import org.http4s.headers.`Content-Length`
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame
import org.typelevel.ci.CIString
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir._
import sttp.tapir.integ.cats.effect.CatsMonadError
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.interceptor.reject.RejectInterceptor
import sttp.tapir.server.interpreter.{BodyListener, FilterServerEndpoints, ServerInterpreter}
import sttp.tapir.server.model.ServerResponse

class Http4sInvalidWebSocketUse(val message: String) extends Exception

final case class InputWithContext[In, Ctx](input: In, context: Ctx)

trait Http4sServerInterpreter[F[_]] {

  // builder to create a ContextRoutes[Ctx, F] instead of a HttpRoutes[F]
  // allowing to delegate this context retieval to http4s (eg. for authentication)
  // the context is put in the request attributes, then retrieved and passed to the endpoint
  final class ContextRoutesBuilder[Ctx](name: String) {

    private val attrKey = new AttributeKey[Ctx](name)

    def toContextRoutes[S, I, E, O, R](
        endpoint: Endpoint[S, I, E, O, R],
        f: Endpoint[S, InputWithContext[I, Ctx], E, O, R] => List[ServerEndpoint[Fs2Streams[F], F]]
    )(implicit dummy: DummyImplicit): ContextRoutes[Ctx, F] = {

      val endpointWithContext =
        endpoint
          .in(extractFromRequest { (req: ServerRequest) =>
            req
              .attribute(attrKey)
              // should never happen since http4s had to build a ContextRequest with Ctx for ContextRoutes
              .getOrElse(throw new RuntimeException(s"context ${name} not found in the request"))
          })
          .mapIn(tuple => (InputWithContext.apply[I, Ctx](_, _)).tupled(tuple))(tuple => (tuple.input, tuple.context))

      innerContextRoutes[Ctx](attrKey, f(endpointWithContext), None)
    }

    def toContextRoutes[S, I, E, O, R](endpoint: Endpoint[S, I, E, O, R])(
        f: Endpoint[S, InputWithContext[I, Ctx], E, O, R] => ServerEndpoint[Fs2Streams[F], F]
    ): ContextRoutes[Ctx, F] =
      toContextRoutes(endpoint, (e: Endpoint[S, InputWithContext[I, Ctx], E, O, R]) => List(f(e)))
  }

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

  def withContext[Ctx](name: String = "defaultContext"): ContextRoutesBuilder[Ctx] =
    new ContextRoutesBuilder[Ctx](name)

  private def toRoutes(
      serverEndpoints: List[ServerEndpoint[Fs2Streams[F] with WebSockets, F]],
      webSocketBuilder: Option[WebSocketBuilder2[F]]
  ): HttpRoutes[F] = {
    implicit val monad: CatsMonadError[F] = new CatsMonadError[F]
    implicit val bodyListener: BodyListener[F, Http4sResponseBody[F]] = new Http4sBodyListener[F]

    val interpreter = new ServerInterpreter[Fs2Streams[F] with WebSockets, F, Http4sResponseBody[F], Fs2Streams[F]](
      FilterServerEndpoints(serverEndpoints),
      new Http4sRequestBody[F](http4sServerOptions),
      new Http4sToResponseBody[F](http4sServerOptions),
      RejectInterceptor.disableWhenSingleEndpoint(http4sServerOptions.interceptors, serverEndpoints),
      http4sServerOptions.deleteFile
    )

    Kleisli { (req: Request[F]) =>
      val serverRequest = Http4sServerRequest(req)

      OptionT(interpreter(serverRequest).flatMap {
        case _: RequestResult.Failure         => none.pure[F]
        case RequestResult.Response(response) => serverResponseToHttp4s(response, webSocketBuilder).map(_.some)
      })
    }
  }

  private def innerContextRoutes[T](
      attributeKey: AttributeKey[T],
      serverEndpoints: List[ServerEndpoint[Fs2Streams[F] with WebSockets, F]],
      webSocketBuilder: Option[WebSocketBuilder2[F]]
  ): ContextRoutes[T, F] = {
    implicit val monad: CatsMonadError[F] = new CatsMonadError[F]
    implicit val bodyListener: BodyListener[F, Http4sResponseBody[F]] = new Http4sBodyListener[F]

    val interpreter = new ServerInterpreter[Fs2Streams[F] with WebSockets, F, Http4sResponseBody[F], Fs2Streams[F]](
      FilterServerEndpoints(serverEndpoints),
      new Http4sRequestBody[F](http4sServerOptions),
      new Http4sToResponseBody[F](http4sServerOptions),
      RejectInterceptor.disableWhenSingleEndpoint(http4sServerOptions.interceptors, serverEndpoints),
      http4sServerOptions.deleteFile
    )

    Kleisli { (contextRequest: ContextRequest[F, T]) =>
      val serverRequest =
        Http4sServerRequest(
          contextRequest.req,
          AttributeMap.Empty
            .put(attributeKey, contextRequest.context)
        )

      OptionT(interpreter(serverRequest).flatMap {
        case _: RequestResult.Failure         => none.pure[F]
        case RequestResult.Response(response) => serverResponseToHttp4s(response, webSocketBuilder).map(_.some)
      })
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
            case None =>
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
