package sttp.tapir.server.netty

import scala.concurrent.{ExecutionContext, Future}

import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.handler.codec.http._
import sttp.monad.FutureMonad
import sttp.tapir.internal.NoStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.interpreter.{BodyListener, ServerInterpreter}

import sttp.tapir.model.ServerResponse
import sttp.tapir.server.netty.NettyServerInterpreter.{NettyRoutingResult, RoutingFailureCode}

trait NettyServerInterpreter {
  def nettyServerOptions: NettyServerOptions = NettyServerOptions.default

  def toHandler(
      ses: List[ServerEndpoint[_, _, _, Any, Future]]
  )(implicit ec: ExecutionContext): FullHttpRequest => NettyRoutingResult = {
    val handler: FullHttpRequest => NettyRoutingResult = { request: FullHttpRequest =>
      implicit val monad: FutureMonad = new FutureMonad()
      implicit val bodyListener: BodyListener[Future, ByteBuf] = new NettyBodyListener
      val serverRequest = new NettyServerRequest(request)
      val serverInterpreter = new ServerInterpreter[Any, Future, ByteBuf, NoStreams](
        new NettyRequestBody(request),
        new NettyToResponseBody,
        nettyServerOptions.interceptors,
        null //todo
      )

      serverInterpreter(serverRequest, ses)
        .map {
          case RequestResult.Response(response) => Right(response)
          case RequestResult.Failure(f)         => Left(RoutingFailureCode)
        }
    }

    handler
  }
}
object NettyServerInterpreter {
  val RoutingFailureCode: Int = 404

  type NettyRoutingResult = Future[Either[Int, ServerResponse[ByteBuf]]]

  def apply(serverOptions: NettyServerOptions = NettyServerOptions.default): NettyServerInterpreter = {
    new NettyServerInterpreter {
      override def nettyServerOptions: NettyServerOptions = serverOptions
    }
  }
}
