package sttp.tapir.server.netty.cats

import cats.effect.Async
import cats.effect.std.Dispatcher
import sttp.monad.MonadError
import sttp.tapir.integ.cats.CatsMonadError
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.Route
import sttp.tapir.server.netty.internal.{NettyServerInterpreter, RunAsync}

trait NettyCatsServerInterpreter[F[_]] {
  implicit def async: Async[F]
  def nettyServerOptions: NettyCatsServerOptions[F, _]

  def toRoute(ses: List[ServerEndpoint[Any, F]]): Route[F] = {
    implicit val monad: MonadError[F] = new CatsMonadError[F]
    val runAsync = new RunAsync[F] {
      override def apply[T](f: => F[T]): Unit = nettyServerOptions.dispatcher.unsafeRunAndForget(f)
    }
    NettyServerInterpreter.toRoute(
      ses,
      nettyServerOptions.interceptors,
      nettyServerOptions.createFile,
      nettyServerOptions.deleteFile,
      runAsync
    )
  }
}

object NettyCatsServerInterpreter {
  def apply[F[_]](dispatcher: Dispatcher[F])(implicit _fa: Async[F]): NettyCatsServerInterpreter[F] = {
    new NettyCatsServerInterpreter[F] {
      override implicit def async: Async[F] = _fa
      override def nettyServerOptions: NettyCatsServerOptions[F, _] = NettyCatsServerOptions.default(dispatcher)(_fa)
    }
  }
  def apply[F[_]](options: NettyCatsServerOptions[F, _])(implicit _fa: Async[F]): NettyCatsServerInterpreter[F] = {
    new NettyCatsServerInterpreter[F] {
      override implicit def async: Async[F] = _fa
      override def nettyServerOptions: NettyCatsServerOptions[F, _] = options
    }
  }
}
