package sttp.tapir.server.netty.internal

import com.typesafe.scalalogging.Logger
import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel.{ChannelFutureListener, ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http._
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.server.model.ServerResponse
import sttp.tapir.server.netty.{NettyServerRequest, Route}

import scala.collection.JavaConverters._
import scala.concurrent.Future

class NettyServerHandler[F[_]](route: Route[F], unsafeRunAsync: (() => F[Unit]) => Unit)(implicit me: MonadError[F])
    extends SimpleChannelInboundHandler[FullHttpRequest] {

  private val logger = Logger[NettyServerHandler[F]]

  private def toHttpResponse(interpreterResponse: ServerResponse[ByteBuf], req: FullHttpRequest): FullHttpResponse = {
    val res = new DefaultFullHttpResponse(
      req.protocolVersion(),
      HttpResponseStatus.valueOf(interpreterResponse.code.code),
      interpreterResponse.body.getOrElse(Unpooled.EMPTY_BUFFER)
    )

    interpreterResponse.headers
      .groupBy(_.name)
      .foreach { case (k, v) =>
        res.headers().set(k, v.map(_.value).asJava)
      }

    res.headers().set(HttpHeaderNames.CONTENT_LENGTH, res.content().readableBytes())

    res
  }

  override def channelRead0(ctx: ChannelHandlerContext, request: FullHttpRequest): Unit = {
    if (HttpUtil.is100ContinueExpected(request)) {
      ctx.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE))
      ()
    } else {
      val req = request.retainedDuplicate()

      unsafeRunAsync { () =>
        route(NettyServerRequest(req))
          .map {
            case Some(response) => response
            case None           => ServerResponse.notFound
          }
          .map(toHttpResponse(_, request))
          .map(flushResponse(ctx, request, _))
          .handleError { case ex: Exception =>
            logger.error("Error while processing the request", ex)
            // send 500
            val res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR)
            res.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0)
            flushResponse(ctx, request, res)
            me.unit(())
          }
      } // exceptions should be handled

      ()
    }
  }

  def flushResponse(ctx: ChannelHandlerContext, req: HttpRequest, res: HttpResponse): Unit = {
    if (!HttpUtil.isKeepAlive(req)) {
      ctx.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE)
      ()
    } else {
      res.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
      ctx.writeAndFlush(res)
      ()
    }
  }
}
