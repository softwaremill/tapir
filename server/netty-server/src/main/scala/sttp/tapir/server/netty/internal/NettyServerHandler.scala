package sttp.tapir.server.netty.internal

import com.typesafe.scalalogging.Logger
import io.netty.buffer.Unpooled
import io.netty.channel.{ChannelFutureListener, ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http._
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.server.model.ServerResponse
import sttp.tapir.server.netty.{NettyServerRequest, Route}

import scala.collection.JavaConverters._

class NettyServerHandler[F[_]](route: Route[F], unsafeRunAsync: (() => F[Unit]) => Unit)(implicit me: MonadError[F])
    extends SimpleChannelInboundHandler[FullHttpRequest] {

  private val logger = Logger[NettyServerHandler[F]]


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
          .map((serverResponse: ServerResponse[HttpChunkedInput]) => {
            writeHeader(ctx, req, serverResponse)
            writeBodyAndFlush(ctx, serverResponse)
          })
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

  private def writeHeader(ctx: ChannelHandlerContext, req: HttpRequest, res: ServerResponse[HttpChunkedInput]): Unit = {
    val response: DefaultHttpResponse = {
      val r = new DefaultHttpResponse(req.protocolVersion(), HttpResponseStatus.valueOf(res.code.code))

      res.headers
        .groupBy(_.name)
        .foreach { case (k, v) =>
          r.headers().set(k, v.map(_.value).asJava)
        }

      if (HttpUtil.isKeepAlive(req)) {
        r.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
      }
      r
    }

    ctx.write(response)
  }

  private def writeBodyAndFlush(ctx: ChannelHandlerContext, res: ServerResponse[HttpChunkedInput]): Unit = {
    res.body match {
      case Some(httpChunkedInput) => ctx.writeAndFlush(httpChunkedInput, ctx.newProgressivePromise())
        .addListener(ChannelFutureListener.CLOSE)
      case None => ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE)
    }

    ()
  }

  private def flushResponse(ctx: ChannelHandlerContext, req: HttpRequest, res: HttpResponse): Unit = {
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
