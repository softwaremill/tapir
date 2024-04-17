package sttp.tapir.server.netty.loom

import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.internal.{NettyToResponseBody, NettyServerInterpreter, RunAsync}

trait NettySyncServerInterpreter {
  def nettyServerOptions: NettySyncServerOptions

  def toRoute(
      ses: List[ServerEndpoint[Any, Id]]
  ): IdRoute = {
    NettyServerInterpreter.toRoute[Id](
      ses,
      nettyServerOptions.interceptors,
      new NettySyncRequestBody(nettyServerOptions.createFile),
      new NettyToResponseBody[Id](RunAsync.Id),
      nettyServerOptions.deleteFile,
      RunAsync.Id
    )
  }
}

object NettySyncServerInterpreter {
  def apply(serverOptions: NettySyncServerOptions = NettySyncServerOptions.default): NettySyncServerInterpreter = {
    new NettySyncServerInterpreter {
      override def nettyServerOptions: NettySyncServerOptions = serverOptions
    }
  }
}
