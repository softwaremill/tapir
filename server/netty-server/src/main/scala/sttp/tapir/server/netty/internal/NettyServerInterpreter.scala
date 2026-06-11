package sttp.tapir.server.netty.internal

import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.TapirFile
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.{Interceptor, RequestResult}
import sttp.tapir.server.interpreter.{BodyListener, FilterServerEndpoints, ServerInterpreter}
import sttp.tapir.server.netty.{NettyResponse, NettyServerRequest, NettyStreams, Route}
import sttp.tapir.server.interpreter.RequestBody
import sttp.tapir.server.interpreter.ToResponseBody

object NettyServerInterpreter {
  def toRoute[F[_]: MonadError](
      ses: List[ServerEndpoint[Any, F]],
      interceptors: List[Interceptor[F]],
      requestBody: RequestBody[F, NettyStreams],
      toResponseBody: ToResponseBody[NettyResponse, NettyStreams],
      deleteFile: TapirFile => F[Unit],
      runAsync: RunAsync[F]
  ): Route[F] = {
    implicit val bodyListener: BodyListener[F, NettyResponse] = new NettyBodyListener[F](runAsync)
    val serverInterpreter = new ServerInterpreter[Any, F, NettyResponse, NettyStreams](
      FilterServerEndpoints(ses),
      requestBody,
      toResponseBody,
      interceptors,
      deleteFile
    )

    val handler: Route[F] = { (request: NettyServerRequest) =>
      serverInterpreter(request)
        .map {
          case RequestResult.Response(response, _) => Some(response)
          case RequestResult.Failure(_)            => None
        }
    }

    handler
  }
}
