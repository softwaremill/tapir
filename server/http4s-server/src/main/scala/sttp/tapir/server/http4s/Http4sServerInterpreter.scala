package sttp.tapir.server.http4s

import cats.arrow.FunctionK
import cats.data.{Kleisli, OptionT}
import cats.effect.std.Queue
import cats.effect.{Async, Sync}
import cats.syntax.all._
import cats.~>
import fs2.{Pipe, Stream}
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame
import org.http4s._
import org.log4s.{Logger, getLogger}
import org.typelevel.ci.CIString
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.{Header => SttpHeader}
import sttp.tapir.Endpoint
import sttp.tapir.model.ServerResponse
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interpreter.{BodyListener, ServerInterpreter}

import scala.reflect.ClassTag

trait Http4sServerInterpreter {
  def toHttp[I, E, O, F[_]: Async, G[_]: Sync](
      e: Endpoint[I, E, O, Fs2Streams[F] with WebSockets]
  )(fToG: F ~> G)(gToF: G ~> F)(logic: I => G[Either[E, O]])(implicit
      serverOptions: Http4sServerOptions[F, G]
  ): Http[OptionT[G, *], F] = toHttp(e.serverLogic(logic))(t)

  def toHttpRecoverErrors[I, E, O, F[_]: Async, G[_]: Sync](
      e: Endpoint[I, E, O, Fs2Streams[F] with WebSockets]
  )(fToG: F ~> G)(gToF: G ~> F)(logic: I => G[O])(implicit
      serverOptions: Http4sServerOptions[F, G],
      eIsThrowable: E <:< Throwable,
      eClassTag: ClassTag[E]
  ): Http[OptionT[G, *], F] = toHttp(e.serverLogicRecoverErrors(logic))(fToG)(gToF)

  def toRoutes[I, E, O, F[_]: Async](e: Endpoint[I, E, O, Fs2Streams[F] with WebSockets])(
      logic: I => F[Either[E, O]]
  )(implicit serverOptions: Http4sServerOptions[F, F]): HttpRoutes[F] = toRoutes(
    e.serverLogic(logic)
  )

  def toRouteRecoverErrors[I, E, O, F[_]: Async](e: Endpoint[I, E, O, Fs2Streams[F] with WebSockets])(logic: I => F[O])(implicit
      serverOptions: Http4sServerOptions[F, F],
      eIsThrowable: E <:< Throwable,
      eClassTag: ClassTag[E]
  ): HttpRoutes[F] = toRoutes(e.serverLogicRecoverErrors(logic))

  //

  def toHttp[I, E, O, F[_]: Async, G[_]: Sync](se: ServerEndpoint[I, E, O, Fs2Streams[F] with WebSockets, G])(fToG: F ~> G)(gToF: G ~> F)(implicit
      serverOptions: Http4sServerOptions[F, G]
  ): Http[OptionT[G, *], F] = toHttp(List(se))(fToG)(gToF)

  def toRoutes[I, E, O, F[_]: Async](
      se: ServerEndpoint[I, E, O, Fs2Streams[F] with WebSockets, F]
  )(implicit serverOptions: Http4sServerOptions[F, F]): HttpRoutes[F] = toRoutes(
    List(se)
  )

  //

  def toRoutes[F[_]: Async](serverEndpoints: List[ServerEndpoint[_, _, _, Fs2Streams[F] with WebSockets, F]])(implicit
      serverOptions: Http4sServerOptions[F, F]
  ): HttpRoutes[F] = {
    val identity = FunctionK.id[F]
    toHttp(serverEndpoints)(identity)(identity)
  }

  //

  def toHttp[F[_]: Async, G[_]: Sync](
      serverEndpoints: List[ServerEndpoint[_, _, _, Fs2Streams[F] with WebSockets, G]]
  )(fToG: F ~> G)(gToF: G ~> F)(implicit
      serverOptions: Http4sServerOptions[F, G]
  ): Http[OptionT[G, *], F] = {
    implicit val monad: CatsMonadError[G] = new CatsMonadError[G]
    implicit val bodyListener: BodyListener[G, Http4sResponseBody[F]] = new Http4sBodyListener[F, G](gToF)

    Kleisli { (req: Request[F]) =>
      val serverRequest = new Http4sServerRequest(req)
      val interpreter = new ServerInterpreter[Fs2Streams[F] with WebSockets, G, Http4sResponseBody[F], Fs2Streams[F]](
        new Http4sRequestBody[F, G](req, serverRequest, serverOptions, fToG),
        new Http4sToResponseBody[F, G](serverOptions),
        serverOptions.interceptors,
        serverOptions.deleteFile
      )

      OptionT(interpreter(serverRequest, serverEndpoints).flatMap {
        case None           => none.pure[G]
        case Some(response) => fToG(serverResponseToHttp4s[F](response)).map(_.some)
      })
    }
  }

  private def serverResponseToHttp4s[F[_]: Async](
      response: ServerResponse[Http4sResponseBody[F]]
  ): F[Response[F]] = {
    val statusCode = statusCodeToHttp4sStatus(response.code)
    val headers = Headers(response.headers.map(h => h.name -> h.value))

    response.body match {
      case Some(Left(pipeF)) =>
        Queue.bounded[F, WebSocketFrame](32).flatMap { queue =>
          pipeF.flatMap { pipe =>
            val send: Stream[F, WebSocketFrame] = Stream.repeatEval(queue.take)
            val receive: Pipe[F, WebSocketFrame, Unit] = pipe.andThen(s => s.evalMap(f => queue.offer(f)))
            WebSocketBuilder[F].copy(headers = headers, filterPingPongs = false).build(send, receive)
          }
        }
      case Some(Right(entity)) =>
        Response(status = statusCode, headers = headers, body = entity).pure[F]

      case None => Response[F](status = statusCode, headers = headers).pure[F]
    }
  }

  private def statusCodeToHttp4sStatus(code: sttp.model.StatusCode): Status =
    Status.fromInt(code.code).getOrElse(throw new IllegalArgumentException(s"Invalid status code: $code"))
}

object Http4sServerInterpreter extends Http4sServerInterpreter {
  private[http4s] val log: Logger = getLogger
}
