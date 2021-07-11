package sttp.tapir.server.ziohttp

import io.netty.handler.codec.http.HttpResponseStatus
import sttp.capabilities.zio.ZioStreams
import sttp.model.{Header => SttpHeader, StatusCode}
import sttp.monad.MonadError
import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.{DecodeFailureContext, ServerInterpreterResult}
import sttp.tapir.server.interpreter.ServerInterpreter
import sttp.tapir.server.ziohttp.ZioHttpInterpreter.zioMonadError
import zhttp.http.{Http, HttpData, HttpError, Request, Response, Status, Header => ZioHttpHeader}
import zio._
import zio.stream.Stream

import scala.reflect.ClassTag

trait ZioHttpInterpreter[R] {

  def zioHttpServerOptions: ZioHttpServerOptions[R] = ZioHttpServerOptions.default

  def toHttp[I, E, O](
      e: Endpoint[I, E, O, ZioStreams]
  )(logic: I => RIO[R, Either[E, O]]): Http[R, Throwable, Request, Response[R, Throwable]] = {
    toHttp(e.serverLogic[RIO[R, *]](input => logic(input)))
  }

  def toHttpRecoverErrors[I, E, O](e: Endpoint[I, E, O, ZioStreams])(
      logic: I => RIO[R, O]
  )(implicit eIsThrowable: E <:< Throwable, eClassTag: ClassTag[E]): Http[R, Throwable, Request, Response[R, Throwable]] =
    toHttp(e.serverLogicRecoverErrors(logic))

  def toHttp(ses: List[ServerEndpoint[_, _, _, ZioStreams, RIO[R, *]]]): Http[R, Throwable, Request, Response[R, Throwable]] =
    ses.map(toHttp(_)).foldLeft(Http.empty: Http[R, Throwable, Request, Response[R, Throwable]])(_ <> _)

  def toHttp[O, E, I](se: ServerEndpoint[I, E, O, ZioStreams, RIO[R, *]]): Http[R, Throwable, Request, Response[R, Throwable]] =
    Http.fromEffectFunction[Request] { req =>
      implicit val bodyListener: ZioHttpBodyListener[R] = new ZioHttpBodyListener[R]
      implicit val monadError: MonadError[RIO[R, *]] = zioMonadError[R]
      val interpreter = new ServerInterpreter[ZioStreams, RIO[R, *], Stream[Throwable, Byte], ZioStreams](
        new ZioHttpRequestBody(req, new ZioHttpServerRequest(req), zioHttpServerOptions),
        new ZioHttpToResponseBody,
        zioHttpServerOptions.interceptors,
        zioHttpServerOptions.deleteFile
      )

      interpreter.apply(new ZioHttpServerRequest(req), se).flatMap {
        case ServerInterpreterResult.Success(resp) =>
          ZIO.succeed(
            Response.HttpResponse(
              status = Status.fromJHttpResponseStatus(HttpResponseStatus.valueOf(resp.code.code)),
              headers = resp.headers.groupBy(_.name).map(sttpToZioHttpHeader).toList,
              content = resp.body.map(stream => HttpData.fromStream(stream)).getOrElse(HttpData.empty)
            )
          )
        case ServerInterpreterResult.Failure(decodeFailureContexts) =>
          val statusCode = DecodeFailureContext.listToStatusCode(decodeFailureContexts)
          val httpError = if (statusCode == StatusCode.MethodNotAllowed) HttpError.MethodNotAllowed() else HttpError.NotFound(req.url.path)
          ZIO.fail(httpError)
      }
    }

  private def sttpToZioHttpHeader(hl: (String, Seq[SttpHeader])): ZioHttpHeader =
    ZioHttpHeader.custom(hl._1, hl._2.map(f => f.value).mkString(", "))

}

object ZioHttpInterpreter {
  def apply[R](serverOptions: ZioHttpServerOptions[R] = ZioHttpServerOptions.default[R]): ZioHttpInterpreter[R] = {
    new ZioHttpInterpreter[R] {
      override def zioHttpServerOptions: ZioHttpServerOptions[R] = serverOptions
    }
  }

  def zioMonadError[R]: MonadError[RIO[R, *]] = new MonadError[RIO[R, *]] {
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
