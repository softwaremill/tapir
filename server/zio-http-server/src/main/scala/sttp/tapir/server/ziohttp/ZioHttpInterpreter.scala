package sttp.tapir.server.ziohttp

import io.netty.handler.codec.http.HttpResponseStatus
import sttp.capabilities.zio.ZioStreams
import sttp.model.{Header => SttpHeader}
import sttp.monad.MonadError
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.interpreter.ServerInterpreter
import sttp.tapir.server.ziohttp.ZioHttpInterpreter.zioMonadError
import sttp.tapir.ztapir._
import zhttp.http.{Http, HttpData, Request, Response, Status, Header => ZioHttpHeader, Headers => ZioHttpHeaders}
import zio._
import zio.stream.Stream

trait ZioHttpInterpreter[R] {

  def zioHttpServerOptions: ZioHttpServerOptions[R] = ZioHttpServerOptions.default

  def toHttp(se: ZServerEndpoint[R, ZioStreams]): Http[R, Throwable, Request, Response] =
    toHttp(List(se))

  def toHttp(ses: List[ZServerEndpoint[R, ZioStreams]]): Http[R, Throwable, Request, Response] = {
    implicit val bodyListener: ZioHttpBodyListener[R] = new ZioHttpBodyListener[R]
    implicit val monadError: MonadError[RIO[R, *]] = zioMonadError[R]
    val interpreter = new ServerInterpreter[ZioStreams, RIO[R, *], Stream[Throwable, Byte], ZioStreams](
      ses,
      new ZioHttpToResponseBody,
      zioHttpServerOptions.interceptors,
      zioHttpServerOptions.deleteFile
    )

    Http.route[Request] { case req =>
      Http
        .fromEffect(
          interpreter
            .apply(new ZioHttpServerRequest(req), new ZioHttpRequestBody(req, new ZioHttpServerRequest(req), zioHttpServerOptions))
            .map {
              case RequestResult.Response(resp) =>
                Http.succeed(
                  Response(
                    status = Status.fromHttpResponseStatus(HttpResponseStatus.valueOf(resp.code.code)),
                    headers = ZioHttpHeaders(resp.headers.groupBy(_.name).map(sttpToZioHttpHeader).toList),
                    data = resp.body.map(stream => HttpData.fromStream(stream)).getOrElse(HttpData.empty)
                  )
                )
              case RequestResult.Failure(_) => Http.empty
            }
        )
        .flatten
    }
  }

  private def sttpToZioHttpHeader(hl: (String, Seq[SttpHeader])): ZioHttpHeader =
    (hl._1, hl._2.map(f => f.value).mkString(", "))

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
    override def eval[T](t: => T): RIO[R, T] = RIO.attempt(t)
    override def suspend[T](t: => RIO[R, T]): RIO[R, T] = RIO.suspend(t)
    override def flatten[T](ffa: RIO[R, RIO[R, T]]): RIO[R, T] = ffa.flatten
    override def ensure[T](f: RIO[R, T], e: => RIO[R, Unit]): RIO[R, T] = f.ensuring(e.ignore)
  }
}
