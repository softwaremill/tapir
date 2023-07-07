package sttp.tapir.server.netty.cats

import cats.effect.Async
import cats.effect.std.Dispatcher
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.integ.cats.effect.CatsMonadError
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.Route
import sttp.tapir.server.interpreter.BodyListener
import sttp.tapir.server.netty.internal.NettyBodyListener
import sttp.tapir.server.netty.NettyResponse
import sttp.tapir.server.interpreter.ServerInterpreter
import sttp.tapir.server.interpreter.FilterServerEndpoints
import sttp.tapir.server.netty.internal.NettyRequestBody
import sttp.tapir.server.netty.internal.NettyToResponseBody
import sttp.tapir.server.interceptor.reject.RejectInterceptor
import sttp.tapir.server.netty.NettyServerRequest
import sttp.tapir.server.interceptor.RequestResult
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.server.netty.internal.RunAsync
import sttp.tapir.server.netty.internal._

trait NettyCatsServerInterpreter[F[_]] {
  implicit def async: Async[F]
  def nettyServerOptions: NettyCatsServerOptions[F]

  def toRoute(ses: List[ServerEndpoint[Fs2Streams[F], F]]): Route[F] = {

    implicit val monad: MonadError[F] = new CatsMonadError[F]
    val runAsync = new RunAsync[F] {
      override def apply[T](f: => F[T]): Unit = nettyServerOptions.dispatcher.unsafeRunAndForget(f)
    }
    implicit val bodyListener: BodyListener[F, NettyResponse] = new NettyBodyListener(runAsync)

    val interceptors = nettyServerOptions.interceptors
    val createFile = nettyServerOptions.createFile
    val deleteFile = nettyServerOptions.deleteFile

    val serverInterpreter = new ServerInterpreter[Fs2Streams[F], F, NettyResponse, Fs2Streams[F]](
      FilterServerEndpoints(ses),
      new NettyCatsRequestBody(createFile),
      new NettyCatsToResponseBody(nettyServerOptions.dispatcher),
      RejectInterceptor.disableWhenSingleEndpoint(interceptors, ses),
      deleteFile
    )

    val handler: Route[F] = { (request: NettyServerRequest) =>
      serverInterpreter(request)
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
      override def nettyServerOptions: NettyCatsServerOptions[F] = NettyCatsServerOptions.default(dispatcher)(_fa)
    }
  }
  def apply[F[_]](options: NettyCatsServerOptions[F])(implicit _fa: Async[F]): NettyCatsServerInterpreter[F] = {
    new NettyCatsServerInterpreter[F] {
      override implicit def async: Async[F] = _fa
      override def nettyServerOptions: NettyCatsServerOptions[F] = options
    }
  }
}
