package sttp.tapir.server.http4s

import cats.arrow.FunctionK
import cats.data.{Kleisli, OptionT}
import cats.effect.{Concurrent, Sync}
import cats.syntax.all._
import cats.~>
import fs2.Pipe
import fs2.concurrent.Queue
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.util.CaseInsensitiveString
import org.http4s.websocket.WebSocketFrame
import org.http4s._
import org.log4s.{Logger, getLogger}
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.{Header => SttpHeader}
import sttp.tapir.Endpoint
import sttp.tapir.model.ServerResponse
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interpreter.ServerInterpreter

import scala.reflect.ClassTag
import cats.effect.Temporal

trait Http4sServerInterpreter {
  def toHttp[I, E, O, F[_], G[_]](e: Endpoint[I, E, O, Fs2Streams[F] with WebSockets])(t: F ~> G)(logic: I => G[Either[E, O]])(implicit
      serverOptions: Http4sServerOptions[F, G],
      gs: Sync[G],
      fs: Concurrent[F],
      timer: Temporal[F]
  ): Http[OptionT[G, *], F] = toHttp(e.serverLogic(logic))(t)

  def toHttpRecoverErrors[I, E, O, F[_], G[_]](e: Endpoint[I, E, O, Fs2Streams[F] with WebSockets])(t: F ~> G)(logic: I => G[O])(implicit
      serverOptions: Http4sServerOptions[F, G],
      gs: Sync[G],
      fs: Concurrent[F],
      eIsThrowable: E <:< Throwable,
      eClassTag: ClassTag[E],
      timer: Temporal[F]
  ): Http[OptionT[G, *], F] = toHttp(e.serverLogicRecoverErrors(logic))(t)

  def toRoutes[I, E, O, F[_]](e: Endpoint[I, E, O, Fs2Streams[F] with WebSockets])(
      logic: I => F[Either[E, O]]
  )(implicit serverOptions: Http4sServerOptions[F, F], fs: Concurrent[F], timer: Temporal[F]): HttpRoutes[F] = toRoutes(
    e.serverLogic(logic)
  )

  def toRouteRecoverErrors[I, E, O, F[_]](e: Endpoint[I, E, O, Fs2Streams[F] with WebSockets])(logic: I => F[O])(implicit
      serverOptions: Http4sServerOptions[F, F],
      fs: Concurrent[F],
      eIsThrowable: E <:< Throwable,
      eClassTag: ClassTag[E],
      timer: Temporal[F]
  ): HttpRoutes[F] = toRoutes(e.serverLogicRecoverErrors(logic))

  //

  def toHttp[I, E, O, F[_], G[_]](se: ServerEndpoint[I, E, O, Fs2Streams[F] with WebSockets, G])(
      t: F ~> G
  )(implicit
      serverOptions: Http4sServerOptions[F, G],
      gs: Sync[G],
      fs: Concurrent[F],
      timer: Temporal[F]
  ): Http[OptionT[G, *], F] = toHttp(List(se))(t)

  def toRoutes[I, E, O, F[_]](
      se: ServerEndpoint[I, E, O, Fs2Streams[F] with WebSockets, F]
  )(implicit serverOptions: Http4sServerOptions[F, F], fs: Concurrent[F], timer: Temporal[F]): HttpRoutes[F] = toRoutes(
    List(se)
  )

  //

  def toRoutes[F[_]](serverEndpoints: List[ServerEndpoint[_, _, _, Fs2Streams[F] with WebSockets, F]])(implicit
      serverOptions: Http4sServerOptions[F, F],
      fs: Concurrent[F],
      timer: Temporal[F]
  ): HttpRoutes[F] = toHttp(serverEndpoints)(FunctionK.id[F])

  //

  def toHttp[F[_], G[_]](serverEndpoints: List[ServerEndpoint[_, _, _, Fs2Streams[F] with WebSockets, G]])(t: F ~> G)(implicit
      serverOptions: Http4sServerOptions[F, G],
      gs: Sync[G],
      fs: Concurrent[F],
      timer: Temporal[F]
  ): Http[OptionT[G, *], F] = {
    implicit val monad: CatsMonadError[G] = new CatsMonadError[G]

    Kleisli { (req: Request[F]) =>
      val serverRequest = new Http4sServerRequest(req)
      val interpreter = new ServerInterpreter[Fs2Streams[F] with WebSockets, G, Http4sResponseBody[F], Fs2Streams[F]](
        new Http4sRequestBody[F, G](req, serverRequest, serverOptions, t),
        new Http4sToResponseBody[F, G](serverOptions),
        serverOptions.interceptors
      )

      OptionT(interpreter(serverRequest, serverEndpoints).flatMap {
        case None           => none.pure[G]
        case Some(response) => t(serverResponseToHttp4s[F](response)).map(_.some)
      })
    }
  }

  private def serverResponseToHttp4s[F[_]: Concurrent](
      response: ServerResponse[Http4sResponseBody[F]]
  ): F[Response[F]] = {
    val statusCode = statusCodeToHttp4sStatus(response.code)
    val headers = Headers(response.headers.map { case SttpHeader(k, v) => Header.Raw(CaseInsensitiveString(k), v) }.toList)

    response.body match {
      case Some(Left(pipeF)) =>
        Queue.bounded[F, WebSocketFrame](32).flatMap { queue =>
          pipeF.flatMap { pipe =>
            val receive: Pipe[F, WebSocketFrame, Unit] = pipe.andThen(s => s.evalMap(f => queue.enqueue1(f)))
            WebSocketBuilder[F].build(queue.dequeue, receive, headers = headers, filterPingPongs = false)
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
