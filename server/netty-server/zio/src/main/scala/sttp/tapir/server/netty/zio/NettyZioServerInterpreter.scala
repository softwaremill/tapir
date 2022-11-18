package sttp.tapir.server.netty.zio

import sttp.tapir.server.netty.Route
import sttp.tapir.server.netty.internal.NettyServerInterpreter
import sttp.tapir.ztapir.{RIOMonadError, ZServerEndpoint}
import zio.RIO

trait NettyZioServerInterpreter[R] {
  def nettyServerOptions: NettyZioServerOptions[R, _]

  def toRoute(ses: List[ZServerEndpoint[R, Any]]): Route[RIO[R, *]] = {
    implicit val monadError: RIOMonadError[R] = new RIOMonadError[R]
    NettyServerInterpreter
      .toRoute(ses, nettyServerOptions.interceptors, nettyServerOptions.createFile, nettyServerOptions.deleteFile)
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
}
