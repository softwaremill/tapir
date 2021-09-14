package sttp.tapir.server

import io.netty.buffer.ByteBuf
import sttp.tapir.model.ServerResponse

import scala.concurrent.{ExecutionContext, Future}

package object netty {
  type Route = NettyServerRequest => Future[Option[ServerResponse[ByteBuf]]]

  object Route {
    def combine(routes: Iterable[Route])(implicit ec: ExecutionContext): Route = { req =>
      def run(rs: List[Route]): Future[Option[ServerResponse[ByteBuf]]] = rs match {
        case head :: tail =>
          head(req).flatMap {
            case Some(response) => Future.successful(Some(response))
            case None           => run(tail)
          }
        case Nil => Future.successful(None)
      }

      run(routes.toList)
    }
  }
}
