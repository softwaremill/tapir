package sttp.tapir.server.netty.internal

import io.netty.buffer.ByteBuf
import sttp.monad.syntax._
import sttp.monad.MonadError
import sttp.tapir.TapirFile
import sttp.tapir.internal.NoStreams
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.{Interceptor, RequestResult}
import sttp.tapir.server.interpreter.{BodyListener, ServerInterpreter}
import sttp.tapir.server.netty.{NettyServerRequest, Route}

object NettyServerInterpreter {
  def toRoute[F[_]: MonadError](
      ses: List[ServerEndpoint[Any, F]],
      interceptors: List[Interceptor[F]],
      createFile: ServerRequest => F[TapirFile],
      deleteFile: TapirFile => F[Unit]
  ): Route[F] = {
    val handler: Route[F] = { (request: NettyServerRequest) =>
      implicit val bodyListener: BodyListener[F, ByteBuf] = new NettyBodyListener
      val serverInterpreter = new ServerInterpreter[Any, F, ByteBuf, NoStreams](
        new NettyRequestBody(request, request, createFile),
        new NettyToResponseBody,
        interceptors,
        deleteFile
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
