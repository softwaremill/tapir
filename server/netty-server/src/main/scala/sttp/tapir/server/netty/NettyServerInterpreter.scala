package sttp.tapir.server.netty

import scala.concurrent.{ExecutionContext, Future}

import io.netty.buffer.ByteBuf
import io.netty.handler.codec.http._
import sttp.monad.FutureMonad
import sttp.tapir.internal.NoStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.interpreter.{BodyListener, ServerInterpreter}
import sttp.tapir.model.ServerResponse
import sttp.tapir.server.netty.NettyServerInterpreter.Route

trait NettyServerInterpreter {
  def nettyServerOptions: NettyServerOptions = NettyServerOptions.default

  def toHandler(
      ses: List[ServerEndpoint[_, _, _, Any, Future]]
  )(implicit ec: ExecutionContext): Route = {
    val handler: Route = { request: FullHttpRequest =>
      implicit val monad: FutureMonad = new FutureMonad()
      implicit val bodyListener: BodyListener[Future, ByteBuf] = new NettyBodyListener
      val serverRequest = new NettyServerRequest(request)
      val serverInterpreter = new ServerInterpreter[Any, Future, ByteBuf, NoStreams](
        new NettyRequestBody(request, serverRequest, nettyServerOptions),
        new NettyToResponseBody,
        nettyServerOptions.interceptors,
        nettyServerOptions.deleteFile
      )

      serverInterpreter(serverRequest, ses)
        .map {
          case RequestResult.Response(response) => Some(response)
          case RequestResult.Failure(f)         => None
        }
    }

    handler
  }
}
object NettyServerInterpreter {
  type Route = FullHttpRequest => Future[Option[ServerResponse[ByteBuf]]]

  def apply(serverOptions: NettyServerOptions = NettyServerOptions.default): NettyServerInterpreter = {
    new NettyServerInterpreter {
      override def nettyServerOptions: NettyServerOptions = serverOptions
    }
  }
}
