package sttp.tapir.server.netty.zio

import sttp.tapir.server.netty.Route
import sttp.tapir.server.netty.internal.{NettyServerInterpreter, RunAsync}
import sttp.tapir.server.netty.zio.NettyZioServerInterpreter.ZioRunAsync
import sttp.tapir.ztapir.{RIOMonadError, ZServerEndpoint}
import zio.{RIO, Runtime}

trait NettyZioServerInterpreter[R] {
  def nettyServerOptions: NettyZioServerOptions[R, _]

  def toRoute(ses: List[ZServerEndpoint[R, Any]])(implicit runtime: Runtime[R]): Route[RIO[R, *]] = {
    implicit val monadError: RIOMonadError[R] = new RIOMonadError[R]
    val runAsync = new ZioRunAsync(runtime)
    NettyServerInterpreter.toRoute(
      ses,
      nettyServerOptions.interceptors,
      nettyServerOptions.createFile,
      nettyServerOptions.deleteFile,
      runAsync
    )
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
    override def apply[T](f: => RIO[R, T]): Unit = runtime.unsafeRunAsync(f)(_ => ())
  }
}
