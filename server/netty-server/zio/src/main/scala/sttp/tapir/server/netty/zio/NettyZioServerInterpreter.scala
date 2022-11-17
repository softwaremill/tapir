package sttp.tapir.server.netty.zio

import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.Route
import sttp.tapir.server.netty.internal.NettyServerInterpreter
import sttp.tapir.ztapir.RIOMonadError
import zio.RIO

trait NettyZioServerInterpreter[R] {
  def nettyServerOptions: NettyZioServerOptions[R, _]

  def toRoute(ses: List[ServerEndpoint[Any, RIO[R, *]]]): Route[RIO[R, *]] = {
    implicit val monadError: RIOMonadError[R] = new RIOMonadError[R]
    NettyServerInterpreter.toRoute(ses, nettyServerOptions.interceptors, nettyServerOptions.createFile, nettyServerOptions.deleteFile)
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
}
