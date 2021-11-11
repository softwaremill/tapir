package sttp.tapir.server.netty

import com.typesafe.scalalogging.Logger
import sttp.monad.{FutureMonad, MonadError}
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.log.{DefaultServerLog, ServerLog, ServerLogInterceptor}
import sttp.tapir.server.interceptor.{CustomInterceptors, Interceptor}
import sttp.tapir.{Defaults, TapirFile}

import scala.concurrent.{Future, blocking}

case class NettyFutureServerOptions(
    host: String,
    port: Int,
    interceptors: List[Interceptor[Future]],
    createFile: ServerRequest => Future[TapirFile],
    deleteFile: TapirFile => Future[Unit],
    nettyOptions: NettyOptions
) {
  def host(s: String): NettyFutureServerOptions = copy(host = s)
  def port(p: Int): NettyFutureServerOptions = copy(port = p)
  def randomPort: NettyFutureServerOptions = port(0)
  def prependInterceptor(i: Interceptor[Future]): NettyFutureServerOptions = copy(interceptors = i :: interceptors)
  def appendInterceptor(i: Interceptor[Future]): NettyFutureServerOptions = copy(interceptors = interceptors :+ i)
  def nettyOptions(o: NettyOptions): NettyFutureServerOptions = copy(nettyOptions = o)
}

object NettyFutureServerOptions {
  val default: NettyFutureServerOptions = customInterceptors.options
  val log = Logger[NettyFutureServerInterpreter]

  def default(interceptors: List[Interceptor[Future]]): NettyFutureServerOptions = NettyFutureServerOptions(
    NettyDefaults.DefaultHost,
    NettyDefaults.DefaultPort,
    interceptors,
    _ => {
      import scala.concurrent.ExecutionContext.Implicits.global
      Future(blocking(Defaults.createTempFile()))
    },
    file => {
      import scala.concurrent.ExecutionContext.Implicits.global
      Future(blocking(Defaults.deleteFile()(file)))
    },
    NettyOptions.default
  )

  def customInterceptors: CustomInterceptors[Future, NettyFutureServerOptions] = {
    CustomInterceptors(
      createLogInterceptor = (sl: ServerLog[Future]) => new ServerLogInterceptor[Future](sl),
      createOptions = (ci: CustomInterceptors[Future, NettyFutureServerOptions]) => default(ci.interceptors)
    ).serverLog(defaultServerLog)
  }

  lazy val defaultServerLog: ServerLog[Future] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    implicit val monadError: MonadError[Future] = new FutureMonad

    DefaultServerLog(
      doLogWhenHandled = debugLog,
      doLogAllDecodeFailures = debugLog,
      doLogExceptions = (msg: String, ex: Throwable) => Future.successful { log.error(msg, ex) }
    )
  }

  private def debugLog(msg: String, exOpt: Option[Throwable]): Future[Unit] = Future.successful {
    NettyDefaults.debugLog(log, msg, exOpt)
  }
}
