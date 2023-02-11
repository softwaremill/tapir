package sttp.tapir.server.netty.zio

import sttp.tapir.server.netty.Route
import sttp.tapir.server.netty.internal.{NettyServerInterpreter, RunAsync}
import sttp.tapir.server.netty.zio.NettyZioServerInterpreter.ZioRunAsync
import sttp.tapir.ztapir.{RIOMonadError, ZServerEndpoint}
import zio.{CancelableFuture, RIO, Runtime, Unsafe, ZIO}

trait NettyZioServerInterpreter[R] {
  def nettyServerOptions: NettyZioServerOptions[R, _]

  def toRoute(ses: List[ZServerEndpoint[R, Any]]): RIO[R, Route[RIO[R, *]]] = ZIO.runtime.map { (runtime: Runtime[R]) =>
    implicit val monadError: RIOMonadError[R] = new RIOMonadError[R]
    val runAsync = new ZioRunAsync(runtime)
    NettyServerInterpreter
      .toRoute(ses, nettyServerOptions.interceptors, nettyServerOptions.createFile, nettyServerOptions.deleteFile, runAsync)
      // we want to log & return a 500 in case of defects as well
      .andThen(_.resurrect)
  }
}

object NettyZioServerInterpreter {
  def apply[R]: NettyZioServerInterpreter[R] = {
    new NettyZioServerInterpreter[R] {
      override def nettyServerOptions: NettyZioServerOptions[R, _] = NettyZioServerOptions.default
    }
  }
  def apply[R](options: NettyZioServerOptions[R, _]): NettyZioServerInterpreter[R] = {
    new NettyZioServerInterpreter[R] {
      override def nettyServerOptions: NettyZioServerOptions[R, _] = options
    }
  }

  private[netty] class ZioRunAsync[R](runtime: Runtime[R]) extends RunAsync[RIO[R, *]] {
    override def apply[T](f: => RIO[R, T]): Unit = Unsafe.unsafe(implicit u => runtime.unsafe.runToFuture(f))
  }
}
