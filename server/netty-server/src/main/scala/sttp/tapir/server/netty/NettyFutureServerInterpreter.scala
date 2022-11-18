package sttp.tapir.server.netty

import sttp.monad.FutureMonad
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.NettyFutureServerInterpreter.FutureRunAsync
import sttp.tapir.server.netty.internal.{NettyServerInterpreter, RunAsync}

import scala.concurrent.{ExecutionContext, Future}

trait NettyFutureServerInterpreter {
  def nettyServerOptions: NettyFutureServerOptions[_]

  def toRoute(
      ses: List[ServerEndpoint[Any, Future]]
  )(implicit ec: ExecutionContext): FutureRoute = {
    implicit val monad: FutureMonad = new FutureMonad()
    NettyServerInterpreter.toRoute(
      ses,
      nettyServerOptions.interceptors,
      nettyServerOptions.createFile,
      nettyServerOptions.deleteFile,
      FutureRunAsync
    )
  }
}

object NettyFutureServerInterpreter {
  def apply(serverOptions: NettyFutureServerOptions[_] = NettyFutureServerOptions.default): NettyFutureServerInterpreter = {
    new NettyFutureServerInterpreter {
      override def nettyServerOptions: NettyFutureServerOptions[_] = serverOptions
    }
  }

  private object FutureRunAsync extends RunAsync[Future] {
    override def apply[T](f: => Future[T]): Unit = f
  }
}
