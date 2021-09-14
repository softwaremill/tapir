package sttp.tapir.server.netty

import scala.concurrent.{ExecutionContext, Future}
import io.netty.buffer.ByteBuf
import sttp.monad.FutureMonad
import sttp.tapir.internal.NoStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.interpreter.{BodyListener, ServerInterpreter}
import sttp.tapir.server.netty.internal.{NettyBodyListener, NettyRequestBody, NettyToResponseBody}

trait NettyServerInterpreter {
  def nettyServerOptions: NettyServerOptions = NettyServerOptions.default

  def toRoute(
      ses: List[ServerEndpoint[_, _, _, Any, Future]]
  )(implicit ec: ExecutionContext): Route = {
    val handler: Route = { (request: NettyServerRequest) =>
      implicit val monad: FutureMonad = new FutureMonad()
      implicit val bodyListener: BodyListener[Future, ByteBuf] = new NettyBodyListener
      val serverInterpreter = new ServerInterpreter[Any, Future, ByteBuf, NoStreams](
        new NettyRequestBody(request, request, nettyServerOptions),
        new NettyToResponseBody,
        nettyServerOptions.interceptors,
        nettyServerOptions.deleteFile
      )

      serverInterpreter(request, ses)
        .map {
          case RequestResult.Response(response) => Some(response)
          case RequestResult.Failure(_)         => None
        }
    }

    handler
  }
}

object NettyServerInterpreter {
  def apply(serverOptions: NettyServerOptions = NettyServerOptions.default): NettyServerInterpreter = {
    new NettyServerInterpreter {
      override def nettyServerOptions: NettyServerOptions = serverOptions
    }
  }
}
