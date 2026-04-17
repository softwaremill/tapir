package sttp.tapir.server

import io.netty.channel.ChannelHandlerContext
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.server.model.ServerResponse

import scala.concurrent.Future

package object netty {
  type Route[F[_]] = NettyServerRequest => F[Option[ServerResponse[NettyResponse]]]
  type FutureRoute = Route[Future]
  type NettyResponse = ChannelHandlerContext => NettyResponseContent

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

    def empty[F[_]](implicit me: MonadError[F]): Route[F] = { req => me.unit(None) }
  }
}
