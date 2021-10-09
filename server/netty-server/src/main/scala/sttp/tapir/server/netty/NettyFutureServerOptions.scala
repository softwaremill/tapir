package sttp.tapir.server.netty

import com.typesafe.scalalogging.Logger
import sttp.monad.{FutureMonad, MonadError}
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.log.{DefaultServerLog, ServerLog, ServerLogInterceptor}
import sttp.tapir.server.interceptor.{CustomiseInterceptors, Interceptor}
import sttp.tapir.{Defaults, TapirFile}

import java.net.{InetSocketAddress, SocketAddress}
import scala.concurrent.{Future, blocking}

case class NettyFutureServerOptions[S <: SocketAddress](
    host: String,
    port: Int,
    interceptors: List[Interceptor[Future]],
    createFile: ServerRequest => Future[TapirFile],
    deleteFile: TapirFile => Future[Unit],
    nettyOptions: NettyOptions[S]
) {
  def host(s: String): NettyFutureServerOptions[S] = copy(host = s)
  def port(p: Int): NettyFutureServerOptions[S] = copy(port = p)
  def randomPort: NettyFutureServerOptions[S] = port(0)
  def prependInterceptor(i: Interceptor[Future]): NettyFutureServerOptions[S] = copy(interceptors = i :: interceptors)
  def appendInterceptor(i: Interceptor[Future]): NettyFutureServerOptions[S] = copy(interceptors = interceptors :+ i)
  def nettyOptions[NEW_S <: SocketAddress](o: NettyOptions[NEW_S]): NettyFutureServerOptions[NEW_S] = copy(nettyOptions = o)
}

object NettyFutureServerOptions {
  val default: NettyFutureServerOptions[InetSocketAddress] = customiseInterceptors.options

  def default(interceptors: List[Interceptor[Future]]): NettyFutureServerOptions[InetSocketAddress] = NettyFutureServerOptions(
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
//    NettyOptions.default
    NettyOptionsBuilder.default.build
  )

  def customiseInterceptors: CustomiseInterceptors[Future, NettyFutureServerOptions] = {
    CustomiseInterceptors(
      createOptions = (ci: CustomiseInterceptors[Future, NettyFutureServerOptions]) => default(ci.interceptors)
    ).serverLog(defaultServerLog)
  }

//  def customInterceptors: CustomInterceptors[Future, Logger => Future[Unit], DefaultOptions] = {
//    CustomInterceptors(
//      createLogInterceptor =
//        (sl: ServerLog[Logger => Future[Unit]]) => new ServerLogInterceptor[Logger => Future[Unit], Future](sl, (_, _) => Future.unit),
//      createOptions = (ci: CustomInterceptors[Future, Logger => Future[Unit], DefaultOptions]) => default(ci.interceptors)
//    ).serverLog(defaultServerLog)
//  }

  private val log = Logger[NettyFutureServerInterpreter]

  lazy val defaultServerLog: ServerLog[Future] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    implicit val monadError: MonadError[Future] = new FutureMonad

    DefaultServerLog(
      doLogWhenReceived = debugLog(_, None),
      doLogWhenHandled = debugLog,
      doLogAllDecodeFailures = debugLog,
      doLogExceptions = (msg: String, ex: Throwable) => Future.successful { log.error(msg, ex) },
      noLog = Future.successful(())
    )
  }

  private def debugLog(msg: String, exOpt: Option[Throwable]): Future[Unit] = Future.successful {
    NettyDefaults.debugLog(log, msg, exOpt)
  }
}
