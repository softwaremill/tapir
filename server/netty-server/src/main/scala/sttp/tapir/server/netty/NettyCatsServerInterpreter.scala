package sttp.tapir.server.netty

import cats.effect.Async
import cats.effect.std.Dispatcher
import sttp.monad.MonadError
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.internal.CatsUtil.CatsMonadError
import sttp.tapir.server.netty.internal.NettyServerInterpreter

import java.net.{InetSocketAddress, SocketAddress}

trait NettyCatsServerInterpreter[F[_], S <: SocketAddress] {
  implicit def async: Async[F]
  def nettyServerOptions: NettyCatsServerOptions[F, S]

  def toRoute(ses: List[ServerEndpoint[Any, F]]): Route[F] = {
    implicit val monad: MonadError[F] = new CatsMonadError[F]
    NettyServerInterpreter.toRoute(ses, nettyServerOptions.interceptors, nettyServerOptions.createFile, nettyServerOptions.deleteFile)
  }
}

object NettyCatsServerInterpreter {
  def apply[F[_]](dispatcher: Dispatcher[F])(implicit _fa: Async[F]): NettyCatsServerInterpreter[F, InetSocketAddress] = {
    new NettyCatsServerInterpreter[F, InetSocketAddress] {
      override implicit def async: Async[F] = _fa
      override def nettyServerOptions: NettyCatsServerOptions[F, InetSocketAddress] = NettyCatsServerOptions.default[F](dispatcher)(async)
    }
  }
  def apply[F[_], S <: SocketAddress](options: NettyCatsServerOptions[F, S])(implicit _fa: Async[F]): NettyCatsServerInterpreter[F, S] = {
    new NettyCatsServerInterpreter[F, S] {
      override implicit def async: Async[F] = _fa
      override def nettyServerOptions: NettyCatsServerOptions[F, S] = options
    }
  }
}
