package sttp.tapir.server.netty

import com.typesafe.scalalogging.Logger
import sttp.monad.{FutureMonad, MonadError}
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.log.{DefaultServerLog, ServerLog, ServerLogInterceptor}
import sttp.tapir.server.interceptor.{CustomiseInterceptors, Interceptor}
import sttp.tapir.{Defaults, TapirFile}

import scala.collection.immutable.List
import scala.concurrent.{Future, blocking}

case class NettyFutureServerOptions(
    interceptors: List[Interceptor[Future]],
    createFile: ServerRequest => Future[TapirFile],
    deleteFile: TapirFile => Future[Unit],
    nettyOptions: NettyOptions
) {
  def prependInterceptor(i: Interceptor[Future]): NettyFutureServerOptions = copy(interceptors = i :: interceptors)
  def appendInterceptor(i: Interceptor[Future]): NettyFutureServerOptions = copy(interceptors = interceptors :+ i)
  def nettyOptions(o: NettyOptions): NettyFutureServerOptions = copy(nettyOptions = o)
}

object NettyFutureServerOptions {
  val defaultTcp: NettyFutureServerOptions = customiseInterceptors(tcp).options
  val defaultUnixSocket: NettyFutureServerOptions = customiseInterceptors(unixSocket).options

  private[netty] def tcp(interceptors: List[Interceptor[Future]]) = default(interceptors, NettyOptionsBuilder.make().tcp().build)
  private def unixSocket(interceptors: List[Interceptor[Future]]) = default(interceptors, NettyOptionsBuilder.make().domainSocket().build)

  private def default(interceptors: List[Interceptor[Future]], nettyOptions: NettyOptions): NettyFutureServerOptions =
    NettyFutureServerOptions(
      interceptors,
      _ => {
        import scala.concurrent.ExecutionContext.Implicits.global
        Future(blocking(Defaults.createTempFile()))
      },
      file => {
        import scala.concurrent.ExecutionContext.Implicits.global
        Future(blocking(Defaults.deleteFile()(file)))
      },
      nettyOptions
    )

  private[netty] def customiseInterceptors(optionsProvider: List[Interceptor[Future]] => NettyFutureServerOptions) = {
    CustomiseInterceptors(
      createOptions = (ci: CustomiseInterceptors[Future, NettyFutureServerOptions]) => optionsProvider(ci.interceptors)
    ).serverLog(defaultServerLog)
  }

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
