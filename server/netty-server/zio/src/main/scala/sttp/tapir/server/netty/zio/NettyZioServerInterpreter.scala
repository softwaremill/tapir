package sttp.tapir.server.netty.zio

import sttp.tapir.server.netty.Route
import sttp.tapir.server.netty.internal.{NettyServerInterpreter, RunAsync}
import sttp.tapir.server.netty.zio.NettyZioServerInterpreter.ZioRunAsync
import sttp.tapir.ztapir.{RIOMonadError, ZServerEndpoint}
import zio._
import sttp.tapir.ztapir._

trait NettyZioServerInterpreter[R] {
  def nettyServerOptions: NettyZioServerOptions[R]

  def toRoute[R2](ses: List[ZServerEndpoint[R2, Any]]): RIO[R & R2, Route[RIO[R & R2, *]]] = ZIO.runtime.map { (runtime: Runtime[R & R2]) =>
    implicit val monadError: RIOMonadError[R & R2] = new RIOMonadError[R & R2]
    val runAsync = new ZioRunAsync[R & R2](runtime)

    val widenedSes = ses.map(_.widen[R & R2])
    val widenedServerOptions = nettyServerOptions.widen[R & R2]

    NettyServerInterpreter
      .toRoute(widenedSes, widenedServerOptions.interceptors, widenedServerOptions.createFile, widenedServerOptions.deleteFile, runAsync)
      // we want to log & return a 500 in case of defects as well
      .andThen(_.resurrect)
  }
}

object NettyZioServerInterpreter {
  def apply[R]: NettyZioServerInterpreter[R] = {
    new NettyZioServerInterpreter[R] {
      override def nettyServerOptions: NettyZioServerOptions[R] = NettyZioServerOptions.default
    }
  }
  def apply[R](options: NettyZioServerOptions[R]): NettyZioServerInterpreter[R] = {
    new NettyZioServerInterpreter[R] {
      override def nettyServerOptions: NettyZioServerOptions[R] = options
    }
  }

  private[netty] class ZioRunAsync[R](runtime: Runtime[R]) extends RunAsync[RIO[R, *]] {
    override def apply[T](f: => RIO[R, T]): Unit = Unsafe.unsafe(implicit u => runtime.unsafe.runToFuture(f))
  }
}
