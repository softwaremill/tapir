package sttp.tapir.server.netty

import sttp.monad.FutureMonad
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.internal.{NettyFutureRequestBody, NettyServerInterpreter, NettyToResponseBody, RunAsync}

import scala.concurrent.{ExecutionContext, Future}

trait NettyFutureServerInterpreter {
  def nettyServerOptions: NettyFutureServerOptions

  def toRoute(se: ServerEndpoint[Any, Future])(implicit ec: ExecutionContext): FutureRoute = {
    toRoute(List(se))
  }

  def toRoute(
      ses: List[ServerEndpoint[Any, Future]]
  )(implicit ec: ExecutionContext): FutureRoute = {
    implicit val monad: FutureMonad = new FutureMonad()
    NettyServerInterpreter.toRoute(
      ses,
      nettyServerOptions.interceptors,
      new NettyFutureRequestBody(nettyServerOptions.createFile),
      new NettyToResponseBody[Future](RunAsync.Future),
      nettyServerOptions.deleteFile,
      RunAsync.Future
    )
  }
}

object NettyFutureServerInterpreter {
  def apply(serverOptions: NettyFutureServerOptions = NettyFutureServerOptions.default): NettyFutureServerInterpreter = {
    new NettyFutureServerInterpreter {
      override def nettyServerOptions: NettyFutureServerOptions = serverOptions
    }
  }
}
