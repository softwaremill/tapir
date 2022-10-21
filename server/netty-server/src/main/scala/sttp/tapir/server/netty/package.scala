package sttp.tapir.server

import io.netty.buffer.ByteBuf
import io.netty.channel.{ChannelFuture, ChannelFutureListener}
import io.netty.handler.codec.http.{
  FullHttpRequest,
  HttpChunkedInput,
  HttpHeaderNames,
  HttpHeaderValues,
  HttpMessage,
  HttpUtil,
  HttpVersion
}
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.server.model.ServerResponse

import scala.concurrent.Future
import scala.collection.JavaConverters._

package object netty {
  type Route[F[_]] = NettyServerRequest => F[Option[ServerResponse[NettyResponse]]]
  type FutureRoute = Route[Future]
  type NettyResponse = Either[ByteBuf, (HttpChunkedInput, Long)]

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

  implicit class RichOptionalNettyResponse(val r: Option[NettyResponse]) {
    def handle(
        byteBufHandler: (ByteBuf) => Unit,
        chunkedInputHandler: (HttpChunkedInput, Long) => Unit,
        noBodyHandler: () => Unit
    ): Unit = {
      r match {
        case Some(value) =>
          value match {
            case Left(byteBuf)                     => byteBufHandler(byteBuf)
            case Right((httpChunkedInput, length)) => chunkedInputHandler(httpChunkedInput, length)
          }
        case None => noBodyHandler()
      }
    }
  }

  implicit class RichHttpMessage(val m: HttpMessage) {
    def setHeadersFrom(response: ServerResponse[_]): Unit = {
      response.headers
        .groupBy(_.name)
        .foreach { case (k, v) =>
          m.headers().set(k, v.map(_.value).asJava)
        }
    }

    def handleContentLengthHeader(length: Long): Unit = {
      if (!m.headers().contains(HttpHeaderNames.CONTENT_LENGTH)) {
        m.headers().set(HttpHeaderNames.CONTENT_LENGTH, length)
      }
    }

    def handleCloseAndKeepAliveHeaders(request: FullHttpRequest): Unit = {
      if (!HttpUtil.isKeepAlive(request))
        m.headers.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
      else if (request.protocolVersion.equals(HttpVersion.HTTP_1_0))
        m.headers.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
    }
  }

  implicit class RichChannelFuture(val cf: ChannelFuture) {
    def closeIfNeeded(request: FullHttpRequest): Unit = {
      if (!HttpUtil.isKeepAlive(request)) {
        cf.addListener(ChannelFutureListener.CLOSE)
      }
    }
  }

}
