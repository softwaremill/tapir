package sttp.tapir.server.netty.internal

import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.TapirFile
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.reject.RejectInterceptor
import sttp.tapir.server.interceptor.{Interceptor, RequestResult}
import sttp.tapir.server.interpreter.{BodyListener, FilterServerEndpoints, ServerInterpreter}
import sttp.tapir.server.netty.{NettyResponse, NettyServerRequest, Route}

object NettyServerInterpreter {
  def toRoute[F[_]: MonadError](
      ses: List[ServerEndpoint[NettyStreams, F]],
      interceptors: List[Interceptor[F]],
      createFile: ServerRequest => F[TapirFile],
      deleteFile: TapirFile => F[Unit]
  ): Route[F] = {
    implicit val bodyListener: BodyListener[F, NettyResponse] = new NettyBodyListener
    val serverInterpreter = new ServerInterpreter[NettyStreams, F, NettyResponse, NettyStreams](
      FilterServerEndpoints(ses),
      new NettyRequestBody(createFile),
      new NettyToResponseBody,
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
