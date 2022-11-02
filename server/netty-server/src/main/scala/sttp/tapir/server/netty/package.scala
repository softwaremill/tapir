package sttp.tapir.server

import io.netty.buffer.ByteBuf
import io.netty.channel.{ChannelHandlerContext, ChannelPromise}
import io.netty.handler.stream.{ChunkedFile, ChunkedStream}
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.server.model.ServerResponse
import sttp.tapir.server.netty.internal.Choice3

import scala.concurrent.Future

package object netty {
  type Route[F[_]] = NettyServerRequest => F[Option[ServerResponse[NettyResponse]]]
  type FutureRoute = Route[Future]
  type NettyResponse = ChannelHandlerContext => (ChannelPromise, Choice3[ByteBuf, ChunkedStream, ChunkedFile])

  object Route {
    def combine[F[_]](routes: Iterable[Route[F]])(implicit me: MonadError[F]): Route[F] = { req =>
      def run(rs: List[Route[F]]): F[Option[ServerResponse[NettyResponse]]] = rs match {
        case head :: tail =>
          head(req).flatMap {
            case Some(response) => me.unit(Some(response))
            case None           => run(tail)
          }
        case Nil => me.unit(None)
      }

      run(routes.toList)
    }
  }
}
