package sttp.tapir.server.netty.zio

import sttp.capabilities.zio.ZioStreams
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.interceptor.reject.RejectInterceptor
import sttp.tapir.server.interpreter.{BodyListener, FilterServerEndpoints, ServerInterpreter}
import sttp.tapir.server.netty.internal.{NettyBodyListener, RunAsync, _}
import sttp.tapir.server.netty.zio.NettyZioServerInterpreter.ZioRunAsync
import sttp.tapir.server.netty.zio.internal.{NettyZioRequestBody, ZioStreamCompatible}
import sttp.tapir.server.netty.{NettyResponse, NettyServerRequest, Route}
import sttp.tapir.ztapir.{RIOMonadError, ZServerEndpoint, _}
import zio._

trait NettyZioServerInterpreter[R] {
  def nettyServerOptions: NettyZioServerOptions[R]

  def toRoute[R2](ses: List[ZServerEndpoint[R2, ZioStreams]]): RIO[R & R2, Route[RIO[R & R2, *]]] = ZIO.runtime.map {
    (runtime: Runtime[R & R2]) =>
      type F[A] = RIO[R & R2, A]
      implicit val monadError: RIOMonadError[R & R2] = new RIOMonadError[R & R2]
      val runAsync = new ZioRunAsync[R & R2](runtime)

      val widenedSes = ses.map(_.widen[R & R2])
      val widenedServerOptions = nettyServerOptions.widen[R & R2]

      implicit val bodyListener: BodyListener[F, NettyResponse] = new NettyBodyListener(runAsync)
      val serverInterpreter = new ServerInterpreter[ZioStreams, F, NettyResponse, ZioStreams](
        FilterServerEndpoints(widenedSes),
        new NettyZioRequestBody(widenedServerOptions.createFile, ZioStreamCompatible(runtime)),
        new NettyToStreamsResponseBody[ZioStreams](ZioStreamCompatible(runtime)),
        RejectInterceptor.disableWhenSingleEndpoint(widenedServerOptions.interceptors, widenedSes),
        widenedServerOptions.deleteFile
      )

      val handler: Route[F] = { (request: NettyServerRequest) =>
        serverInterpreter(request)
          .map {
            case RequestResult.Response(response) => Some(response)
            case RequestResult.Failure(_)         => None
          }
      }

      handler
        // we want to log & return a 500 in case of defects as well
        .andThen(_.resurrect)
  }
}

object NettyZioServerInterpreter {
  def apply[R]: NettyZioServerInterpreter[R] = {
    new NettyZioServerInterpreter[R] {
      override def nettyServerOptions: NettyZioServerOptions[R] = NettyZioServerOptions.default
    }
  }
  def apply[R](options: NettyZioServerOptions[R]): NettyZioServerInterpreter[R] = {
    new NettyZioServerInterpreter[R] {
      override def nettyServerOptions: NettyZioServerOptions[R] = options
    }
  }

  private[netty] class ZioRunAsync[R](runtime: Runtime[R]) extends RunAsync[RIO[R, *]] {
    override def apply[T](f: => RIO[R, T]): Unit = Unsafe.unsafe(implicit u => runtime.unsafe.runToFuture(f))
  }
}
