package sttp.tapir.server.netty.internal

import com.typesafe.scalalogging.Logger
import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel._
import io.netty.handler.codec.http._
import io.netty.handler.stream.{ChunkedFile, ChunkedStream}
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.server.model.ServerResponse
import sttp.tapir.server.netty.{NettyResponse, NettyServerRequest, Route}

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
          .map((serverResponse: ServerResponse[NettyResponse]) => {
            serverResponse.handle(
              ctx = ctx,
              byteBufHandler = (byteBuf) => {
                val res = new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.valueOf(serverResponse.code.code), byteBuf)

                res.setHeadersFrom(serverResponse)
                res.handleContentLengthHeader(byteBuf.readableBytes())
                res.handleCloseAndKeepAliveHeaders(request)

                ctx.writeAndFlush(res).closeIfNeeded(req)
              },
              chunkedStreamHandler = (channelPromise, chunkedStream) => {
                val resHeader: DefaultHttpResponse =
                  new DefaultHttpResponse(req.protocolVersion(), HttpResponseStatus.valueOf(serverResponse.code.code))

                resHeader.setHeadersFrom(serverResponse)
                resHeader.setChunked()
                resHeader.handleCloseAndKeepAliveHeaders(request)

                ctx.write(resHeader)
                ctx.writeAndFlush(new HttpChunkedInput(chunkedStream), channelPromise).closeIfNeeded(req)
              },
              chunkedFileHandler = (channelPromise, chunkedFile) => {
                val resHeader: DefaultHttpResponse =
                  new DefaultHttpResponse(req.protocolVersion(), HttpResponseStatus.valueOf(serverResponse.code.code))

                resHeader.setHeadersFrom(serverResponse)
                resHeader.handleContentLengthHeader(chunkedFile.length())
                resHeader.handleCloseAndKeepAliveHeaders(request)

                ctx.write(resHeader)
                // HttpChunkedInput will write the end marker (LastHttpContent) for us.
                ctx.writeAndFlush(new HttpChunkedInput(chunkedFile), channelPromise).closeIfNeeded(req)
              },
              noBodyHandler = () => {
                val res = new DefaultFullHttpResponse(
                  req.protocolVersion(),
                  HttpResponseStatus.valueOf(serverResponse.code.code),
                  Unpooled.EMPTY_BUFFER
                )

                res.setHeadersFrom(serverResponse)
                res.handleContentLengthHeader(Unpooled.EMPTY_BUFFER.readableBytes())
                res.handleCloseAndKeepAliveHeaders(req)

                ctx.writeAndFlush(res).closeIfNeeded(req)
              })
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

  private implicit class RichServerNettyResponse(val r: ServerResponse[NettyResponse]) {
    def handle(ctx: ChannelHandlerContext,
               byteBufHandler: (ByteBuf) => Unit,
               chunkedStreamHandler: (ChannelPromise, ChunkedStream) => Unit,
               chunkedFileHandler: (ChannelPromise, ChunkedFile) => Unit,
               noBodyHandler: () => Unit
    ): Unit = {
      r.body match {
        case Some(function) => {
          val values = function(ctx)

          values match {
            case (_, Left(byteBuf)) => byteBufHandler(byteBuf)
            case (channelPromise, Middle(chunkedStream)) => chunkedStreamHandler(channelPromise, chunkedStream)
            case (channelPromise, Right(chunkedFile)) => chunkedFileHandler(channelPromise, chunkedFile)
          }
        }
        case None => noBodyHandler()
      }
    }
  }

  private implicit class RichHttpMessage(val m: HttpMessage) {
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

    def setChunked(): Unit = {
      m.headers().remove(HttpHeaderNames.CONTENT_LENGTH)
      m.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
    }

    def handleCloseAndKeepAliveHeaders(request: FullHttpRequest): Unit = {
      if (!HttpUtil.isKeepAlive(request))
        m.headers.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
      else if (request.protocolVersion.equals(HttpVersion.HTTP_1_0))
        m.headers.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
    }
  }

  private implicit class RichChannelFuture(val cf: ChannelFuture) {
    def closeIfNeeded(request: FullHttpRequest): Unit = {
      if (!HttpUtil.isKeepAlive(request)) {
        cf.addListener(ChannelFutureListener.CLOSE)
      }
    }
  }
}
