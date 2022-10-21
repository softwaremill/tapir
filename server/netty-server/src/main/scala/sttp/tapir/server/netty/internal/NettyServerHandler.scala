package sttp.tapir.server.netty.internal

import com.typesafe.scalalogging.Logger
import io.netty.buffer.Unpooled
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http._
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.server.model.ServerResponse
import sttp.tapir.server.netty.{NettyResponse, NettyServerRequest, RichChannelFuture, RichHttpMessage, RichOptionalNettyResponse, Route}

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
          .map((serverResponse: ServerResponse[NettyResponse]) => {
            serverResponse.body.handle(
              byteBufHandler = (byteBuf) => {
                val res = new DefaultFullHttpResponse(
                  req.protocolVersion(),
                  HttpResponseStatus.valueOf(serverResponse.code.code),
                  byteBuf)

                res.setHeadersFrom(serverResponse)
                res.handleContentLengthHeader(byteBuf.readableBytes())
                res.handleCloseAndKeepAliveHeaders(request)

                ctx.writeAndFlush(res).closeIfNeeded(req)
              },
              chunkedInputHandler = (httpChunkedInput, length) => {
                val resHeader: DefaultHttpResponse =
                  new DefaultHttpResponse(req.protocolVersion(), HttpResponseStatus.valueOf(serverResponse.code.code))

                resHeader.setHeadersFrom(serverResponse)
                resHeader.handleContentLengthHeader(length)
                resHeader.handleCloseAndKeepAliveHeaders(request)

                ctx.write(resHeader)
                ctx.writeAndFlush(httpChunkedInput, ctx.newProgressivePromise()).closeIfNeeded(req)
              },
              noBodyHandler = () => {
                val res = new DefaultFullHttpResponse(
                  req.protocolVersion(),
                  HttpResponseStatus.valueOf(serverResponse.code.code),
                  Unpooled.EMPTY_BUFFER)

                res.setHeadersFrom(serverResponse)
                res.handleContentLengthHeader(Unpooled.EMPTY_BUFFER.readableBytes())
                res.handleCloseAndKeepAliveHeaders(req)

                ctx.writeAndFlush(res).closeIfNeeded(req)
              }
            )
          })
          .handleError { case ex: Exception =>
            logger.error("Error while processing the request", ex)
            // send 500
            val res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR)
            res.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0)
            res.handleCloseAndKeepAliveHeaders(req)

            ctx.writeAndFlush(res).closeIfNeeded(req)
            me.unit(())
          }
      } // exceptions should be handled

      ()
    }
  }
}
