package sttp.tapir.server.netty

import cats.effect.Async
import cats.effect.std.Dispatcher
import sttp.monad.MonadError
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.internal.CatsUtil.CatsMonadError
import sttp.tapir.server.netty.internal.NettyServerInterpreter

trait NettyCatsServerInterpreter[F[_]] {
  implicit def async: Async[F]
  def nettyServerOptions: NettyCatsServerOptions[F]

  def toRoute(ses: List[ServerEndpoint[_, _, _, _, _, Any, F]]): Route[F] = {
    implicit val monad: MonadError[F] = new CatsMonadError[F]
    NettyServerInterpreter.toRoute(ses, nettyServerOptions.interceptors, nettyServerOptions.createFile, nettyServerOptions.deleteFile)
  }
}

object NettyCatsServerInterpreter {
  def apply[F[_]](dispatcher: Dispatcher[F])(implicit _fa: Async[F]): NettyCatsServerInterpreter[F] = {
    new NettyCatsServerInterpreter[F] {
      override implicit def async: Async[F] = _fa
      override def nettyServerOptions: NettyCatsServerOptions[F] = NettyCatsServerOptions.default[F](dispatcher)(async)
    }
  }
  def apply[F[_]](options: NettyCatsServerOptions[F])(implicit _fa: Async[F]): NettyCatsServerInterpreter[F] = {
    new NettyCatsServerInterpreter[F] {
      override implicit def async: Async[F] = _fa
      override def nettyServerOptions: NettyCatsServerOptions[F] = options
    }
  }
}
