package sttp.tapir.server.zhttp

import io.netty.handler.codec.http.HttpResponseStatus
import sttp.capabilities.zio.ZioStreams
import sttp.model.{Header => SttpHeader}
import sttp.monad.MonadError
import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interpreter.ServerInterpreter
import sttp.tapir.server.zhttp.ZHttpInterpreter.zioMonadError
import zhttp.http.{Http, HttpData, HttpError, Request, Response, Status, Header => ZHttpHeader}
import zio._
import zio.blocking.Blocking
import zio.stream.ZStream

import scala.reflect.ClassTag

trait ZHttpInterpreter[R <: Blocking] {

  def toRouteRecoverErrors[I, E, O](
      e: Endpoint[I, E, O, ZioStreams]
  )(
      logic: I => RIO[R, O]
  )(implicit eIsThrowable: E <:< Throwable, eClassTag: ClassTag[E]): Http[R, Throwable, Request, Response[R, Throwable]] = {
    toRoutes(e.serverLogicRecoverErrors(logic))
  }

  def toRoutes[I, E, O](
      se: ServerEndpoint[I, E, O, ZioStreams, RIO[R, *]]
  ): Http[R, Throwable, Request, Response[R, Throwable]] = {
    toHttp(se)
  }

  def zHttpServerOptions: ZHttpServerOptions[R] = ZHttpServerOptions.default

  private def sttpToZHttpHeader(header: SttpHeader): ZHttpHeader =
    ZHttpHeader(header.name, header.value)

  def toRoutes[I, O](
      e: Endpoint[I, Throwable, O, ZioStreams]
  )(logic: I => RIO[R, O]): Http[R, Throwable, Request, Response[R, Throwable]] = {
    toHttp(e.serverLogic[RIO[R, *]](input => logic(input).either))
  }

  private def toHttp[O, E, I](
      se: ServerEndpoint[I, E, O, ZioStreams, RIO[R, *]]
  ): Http[R, Throwable, Request, Response[R, Throwable]] =
    Http.fromEffectFunction[Request] { req =>
      implicit val interpret: ZHttpBodyListener[R] = new ZHttpBodyListener[R]
      implicit val monadError: MonadError[RIO[R, *]] = zioMonadError[R]
      val interpreter = new ServerInterpreter[ZioStreams, RIO[R, *], ZStream[Blocking, Throwable, Byte], ZioStreams](
        new ZHttpRequestBody(req),
        new ZHttpToResponseBody,
        zHttpServerOptions.interceptors,
        zHttpServerOptions.deleteFile
      )

      interpreter.apply(new ZHttpServerRequest(req), se).flatMap {
        case Some(resp) =>
          ZIO.succeed(
            Response.HttpResponse(
              status = Status.fromJHttpResponseStatus(HttpResponseStatus.valueOf(resp.code.code)),
              headers = resp.headers.map(sttpToZHttpHeader).toList,
              content = resp.body.map(stream => HttpData.fromStream(stream)).getOrElse(HttpData.empty)
            )
          )
        case None => ZIO.fail(HttpError.NotFound(req.url.path))
      }
    }

}

object ZHttpInterpreter {
  def apply[R <: Blocking](serverOptions: ZHttpServerOptions[R] = ZHttpServerOptions.default[R]): ZHttpInterpreter[R] = {
    new ZHttpInterpreter[R] {
      override def zHttpServerOptions: ZHttpServerOptions[R] = serverOptions
    }
  }

  private[zhttp] def zioMonadError[R]: MonadError[RIO[R, *]] = new MonadError[RIO[R, *]] {
    override def unit[T](t: T): RIO[R, T] = URIO.succeed(t)
    override def map[T, T2](fa: RIO[R, T])(f: T => T2): RIO[R, T2] = fa.map(f)
    override def flatMap[T, T2](fa: RIO[R, T])(f: T => RIO[R, T2]): RIO[R, T2] = fa.flatMap(f)
    override def error[T](t: Throwable): RIO[R, T] = RIO.fail(t)
    override protected def handleWrappedError[T](rt: RIO[R, T])(h: PartialFunction[Throwable, RIO[R, T]]): RIO[R, T] = rt.catchSome(h)
    override def eval[T](t: => T): RIO[R, T] = RIO.effect(t)
    override def suspend[T](t: => RIO[R, T]): RIO[R, T] = RIO.effectSuspend(t)
    override def flatten[T](ffa: RIO[R, RIO[R, T]]): RIO[R, T] = ffa.flatten
    override def ensure[T](f: RIO[R, T], e: => RIO[R, Unit]): RIO[R, T] = f.ensuring(e.catchAll(_ => ZIO.unit))
  }
}
