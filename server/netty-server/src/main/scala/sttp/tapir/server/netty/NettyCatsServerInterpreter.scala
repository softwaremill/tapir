package sttp.tapir.server.netty

import cats.effect.Async
import cats.effect.std.Dispatcher
import io.netty.buffer.ByteBuf
import sttp.monad.syntax._
import sttp.monad.MonadError
import sttp.tapir.internal.NoStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.interpreter.{BodyListener, ServerInterpreter}
import sttp.tapir.server.netty.internal.CatsUtil.CatsMonadError
import sttp.tapir.server.netty.internal.{NettyBodyListener, NettyRequestBody, NettyToResponseBody}

trait NettyCatsServerInterpreter[F[_]] {
  implicit def async: Async[F]
  def nettyServerOptions: NettyCatsServerOptions[F]

  def toRoute(ses: List[ServerEndpoint[_, _, _, Any, F]]): Route[F] = {
    val handler: Route[F] = { (request: NettyServerRequest) =>
      implicit val monad: MonadError[F] = new CatsMonadError[F]
      implicit val bodyListener: BodyListener[F, ByteBuf] = new NettyBodyListener
      val serverInterpreter = new ServerInterpreter[Any, F, ByteBuf, NoStreams](
        new NettyRequestBody(request, request, nettyServerOptions.createFile),
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

object NettyCatsServerInterpreter {
  def apply[F[_]](dispatcher: Dispatcher[F])(implicit _fa: Async[F]): NettyCatsServerInterpreter[F] = {
    new NettyCatsServerInterpreter[F] {
      override implicit def async: Async[F] = _fa
      override def nettyServerOptions: NettyCatsServerOptions[F] = NettyCatsServerOptions.default[F](dispatcher)(async)
    }
  }
  def apply[F[_]](options: NettyCatsServerOptions[F])(implicit _fa: Async[F]): NettyCatsServerInterpreter[F] = {
    new NettyCatsServerInterpreter[F] {
      override implicit def async: Async[F] = _fa
      override def nettyServerOptions: NettyCatsServerOptions[F] = options
    }
  }
}
