package sttp.tapir.server.netty.loom

import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.internal.{NettyToResponseBody, NettyServerInterpreter, RunAsync}

trait NettyIdServerInterpreter {
  def nettyServerOptions: NettyIdServerOptions

  def toRoute(
      ses: List[ServerEndpoint[Any, Id]]
  ): IdRoute = {
    NettyServerInterpreter.toRoute[Id](
      ses,
      nettyServerOptions.interceptors,
      new NettyIdRequestBody(nettyServerOptions.createFile),
      new NettyToResponseBody[Id],
      nettyServerOptions.deleteFile,
      new RunAsync[Id] {
        override def apply[T](f: => Id[T]): Unit = {
          val _ = f
          ()
        }
      }
    )
  }
}

object NettyIdServerInterpreter {
  def apply(serverOptions: NettyIdServerOptions = NettyIdServerOptions.default): NettyIdServerInterpreter = {
    new NettyIdServerInterpreter {
      override def nettyServerOptions: NettyIdServerOptions = serverOptions
    }
  }
}
