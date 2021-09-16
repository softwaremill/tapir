package sttp.tapir.server.http4s

import cats.data.{Kleisli, OptionT}
import cats.effect.std.Queue
import cats.effect.{Async, Sync}
import cats.implicits._
import cats.~>
import fs2.{Pipe, Stream}
import org.http4s._
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame
import org.log4s.{Logger, getLogger}
import org.typelevel.ci.CIString
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.Endpoint
import sttp.tapir.integ.cats.CatsMonadError
import sttp.tapir.model.ServerResponse
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.interpreter.{BodyListener, ServerInterpreter}

import scala.reflect.ClassTag

trait Http4sServerToHttpInterpreter[F[_], G[_]] {

  implicit def fa: Async[F]
  implicit def gs: Sync[G]

  def fToG: F ~> G
  def gToF: G ~> F

  def http4sServerOptions: Http4sServerOptions[F, G] = Http4sServerOptions.default[F, G]

  def toHttp[I, E, O](
      e: Endpoint[I, E, O, Fs2Streams[F] with WebSockets]
  )(logic: I => G[Either[E, O]]): Http[OptionT[G, *], F] = toHttp(e.serverLogic(logic))

  def toHttpRecoverErrors[I, E, O](
      e: Endpoint[I, E, O, Fs2Streams[F] with WebSockets]
  )(logic: I => G[O])(implicit
      eIsThrowable: E <:< Throwable,
      eClassTag: ClassTag[E]
  ): Http[OptionT[G, *], F] = toHttp(e.serverLogicRecoverErrors(logic))

  //

  def toHttp[I, E, O](se: ServerEndpoint[I, E, O, Fs2Streams[F] with WebSockets, G]): Http[OptionT[G, *], F] = toHttp(List(se))(fToG)(gToF)

  def toHttp(
      serverEndpoints: List[ServerEndpoint[_, _, _, Fs2Streams[F] with WebSockets, G]]
  )(fToG: F ~> G)(gToF: G ~> F): Http[OptionT[G, *], F] = {
    implicit val monad: CatsMonadError[G] = new CatsMonadError[G]
    implicit val bodyListener: BodyListener[G, Http4sResponseBody[F]] = new Http4sBodyListener[F, G](gToF)

    Kleisli { (req: Request[F]) =>
      val serverRequest = new Http4sServerRequest(req)
      val interpreter = new ServerInterpreter[Fs2Streams[F] with WebSockets, G, Http4sResponseBody[F], Fs2Streams[F]](
        new Http4sRequestBody[F, G](req, serverRequest, http4sServerOptions, fToG),
        new Http4sToResponseBody[F, G](http4sServerOptions),
        http4sServerOptions.interceptors,
        http4sServerOptions.deleteFile
      )

      OptionT(interpreter(serverRequest, serverEndpoints).flatMap {
        case _: RequestResult.Failure         => none.pure[G]
        case RequestResult.Response(response) => fToG(serverResponseToHttp4s(response)).map(_.some)
      })
    }
  }

  private def serverResponseToHttp4s(
      response: ServerResponse[Http4sResponseBody[F]]
  ): F[Response[F]] = {
    val statusCode = statusCodeToHttp4sStatus(response.code)
    val headers = Headers(response.headers.map(header => Header.Raw(CIString(header.name), header.value)).toList)

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

object Http4sServerToHttpInterpreter {

  private[http4s] val log: Logger = getLogger

  def apply[F[_], G[_]]()(_fToG: F ~> G)(_gToF: G ~> F)(implicit
      _fa: Async[F],
      _gs: Sync[G]
  ): Http4sServerToHttpInterpreter[F, G] = {
    new Http4sServerToHttpInterpreter[F, G] {
      override implicit def gs: Sync[G] = _gs
      override implicit def fa: Async[F] = _fa
      override def fToG: F ~> G = _fToG
      override def gToF: G ~> F = _gToF
    }
  }

  def apply[F[_], G[_]](serverOptions: Http4sServerOptions[F, G])(_fToG: F ~> G)(_gToF: G ~> F)(implicit
      _fa: Async[F],
      _gs: Sync[G]
  ): Http4sServerToHttpInterpreter[F, G] = {
    new Http4sServerToHttpInterpreter[F, G] {
      override implicit def gs: Sync[G] = _gs
      override implicit def fa: Async[F] = _fa
      override def fToG: F ~> G = _fToG
      override def gToF: G ~> F = _gToF
      override def http4sServerOptions: Http4sServerOptions[F, G] = serverOptions
    }
  }
}
