package sttp.tapir.server.netty

import sttp.monad.FutureMonad
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.internal.NettyServerInterpreter

import scala.concurrent.{ExecutionContext, Future}

trait NettyFutureServerInterpreter {
  def nettyServerOptions: NettyFutureServerOptions

  def toRoute(
      ses: List[ServerEndpoint[Any, Future]]
  )(implicit ec: ExecutionContext): FutureRoute = {
    implicit val monad: FutureMonad = new FutureMonad()
    NettyServerInterpreter.toRoute(ses, nettyServerOptions.interceptors, nettyServerOptions.createFile, nettyServerOptions.deleteFile)
  }
}

object NettyFutureServerInterpreter {
  def apply(serverOptions: NettyFutureServerOptions = NettyFutureServerOptions.defaultTcp): NettyFutureServerInterpreter = {
    new NettyFutureServerInterpreter {
      override def nettyServerOptions: NettyFutureServerOptions = serverOptions
    }
  }
}
