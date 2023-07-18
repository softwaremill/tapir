package sttp.tapir.server.netty.internal

import com.typesafe.netty.http.{DefaultStreamedHttpResponse, StreamedHttpRequest}
import com.typesafe.scalalogging.Logger
import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel._
import io.netty.handler.codec.http.HttpHeaderNames.{CONNECTION, CONTENT_LENGTH}
import io.netty.handler.codec.http._
import io.netty.handler.stream.{ChunkedFile, ChunkedStream}
import org.reactivestreams.Publisher
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.server.model.ServerResponse
import sttp.tapir.server.netty.NettyResponseContent.{
  ByteBufNettyResponseContent,
  ChunkedFileNettyResponseContent,
  ChunkedStreamNettyResponseContent,
  ReactivePublisherNettyResponseContent
}
import sttp.tapir.server.netty.{NettyResponse, NettyServerRequest, Route}

import scala.collection.JavaConverters._

class NettyServerHandler[F[_]](route: Route[F], unsafeRunAsync: (() => F[Unit]) => Unit, maxContentLength: Option[Int])(implicit
    me: MonadError[F]
) extends SimpleChannelInboundHandler[HttpRequest] {

  private val logger = Logger[NettyServerHandler[F]]

  private val EntityTooLarge: FullHttpResponse = {
    val res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, Unpooled.EMPTY_BUFFER)
    res.headers().set(CONTENT_LENGTH, 0)
    res
  }

  private val EntityTooLargeClose: FullHttpResponse = {
    val res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, Unpooled.EMPTY_BUFFER)
    res.headers().set(CONTENT_LENGTH, 0)
    res.headers().set(CONNECTION, HttpHeaderValues.CLOSE)
    res
  }

  override def channelRead0(ctx: ChannelHandlerContext, request: HttpRequest): Unit = {

    def runRoute(req: HttpRequest) = {

      route(NettyServerRequest(req))
        .map {
          case Some(response) => response
          case None           => ServerResponse.notFound
        }
        .flatMap((serverResponse: ServerResponse[NettyResponse]) =>
          // in ZIO, exceptions thrown in .map become defects - instead, we want them represented as errors so that
          // we get the 500 response, instead of dropping the request
          try handleResponse(ctx, req, serverResponse).unit
          catch {
            case e: Exception => me.error[Unit](e)
          }
        )
        .handleError { case ex: Exception =>
          logger.error("Error while processing the request", ex)
          // send 500
          val res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR)
          res.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0)
          res.handleCloseAndKeepAliveHeaders(req)

          ctx.writeAndFlush(res).closeIfNeeded(req)
          me.unit(())
        }
    }

    if (HttpUtil.is100ContinueExpected(request)) {
      ctx.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE))
      ()
    } else {
      request match {
        case full: FullHttpRequest =>
          val req = full.retain()
          unsafeRunAsync { () =>
            runRoute(req)
              .ensure(me.eval(req.release()))
          } // exceptions should be handled
        case req: StreamedHttpRequest =>
          unsafeRunAsync { () =>
            runRoute(req)
          }
        case _ => throw new UnsupportedOperationException(s"Unexpected Netty request type: ${request.getClass.getName}")
      }

      ()
    }
  }

  private def handleResponse(ctx: ChannelHandlerContext, req: HttpRequest, serverResponse: ServerResponse[NettyResponse]): Unit =
    serverResponse.handle(
      ctx = ctx,
      byteBufHandler = (channelPromise, byteBuf) => {

        if (maxContentLength.exists(_ < byteBuf.readableBytes))
          writeEntityTooLargeResponse(ctx, req)
        else {
          val res = new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.valueOf(serverResponse.code.code), byteBuf)
          res.setHeadersFrom(serverResponse)
          res.handleContentLengthAndChunkedHeaders(Option(byteBuf.readableBytes()))
          res.handleCloseAndKeepAliveHeaders(req)
          ctx.writeAndFlush(res, channelPromise).closeIfNeeded(req)
        }
      },
      chunkedStreamHandler = (channelPromise, chunkedStream) => {
        val resHeader: DefaultHttpResponse =
          new DefaultHttpResponse(req.protocolVersion(), HttpResponseStatus.valueOf(serverResponse.code.code))

        resHeader.setHeadersFrom(serverResponse)
        resHeader.handleContentLengthAndChunkedHeaders(None)
        resHeader.handleCloseAndKeepAliveHeaders(req)

        ctx.write(resHeader)
        ctx.writeAndFlush(new HttpChunkedInput(chunkedStream), channelPromise).closeIfNeeded(req)
      },
      chunkedFileHandler = (channelPromise, chunkedFile) => {
        val resHeader: DefaultHttpResponse =
          new DefaultHttpResponse(req.protocolVersion(), HttpResponseStatus.valueOf(serverResponse.code.code))

        resHeader.setHeadersFrom(serverResponse)
        resHeader.handleContentLengthAndChunkedHeaders(Option(chunkedFile.length()))
        resHeader.handleCloseAndKeepAliveHeaders(req)
        ctx.write(resHeader)
        // HttpChunkedInput will write the end marker (LastHttpContent) for us.
        ctx.writeAndFlush(new HttpChunkedInput(chunkedFile), channelPromise).closeIfNeeded(req)
      },
      reactiveStreamHandler = (channelPromise, publisher) => {
        val res: DefaultStreamedHttpResponse =
          new DefaultStreamedHttpResponse(req.protocolVersion(), HttpResponseStatus.valueOf(serverResponse.code.code), publisher)

        res.setHeadersFrom(serverResponse)
        res.handleContentLengthAndChunkedHeaders(None)
        res.handleCloseAndKeepAliveHeaders(req)
        ctx.writeAndFlush(res, channelPromise).closeIfNeeded(req)

      },
      noBodyHandler = () => {
        val res = new DefaultFullHttpResponse(
          req.protocolVersion(),
          HttpResponseStatus.valueOf(serverResponse.code.code),
          Unpooled.EMPTY_BUFFER
        )

        res.setHeadersFrom(serverResponse)
        res.handleContentLengthAndChunkedHeaders(Option(Unpooled.EMPTY_BUFFER.readableBytes()))
        res.handleCloseAndKeepAliveHeaders(req)

        ctx.writeAndFlush(res).closeIfNeeded(req)
      }
    )

  private def writeEntityTooLargeResponse(ctx: ChannelHandlerContext, req: HttpRequest): Unit = {

    if (!HttpUtil.is100ContinueExpected(req) && !HttpUtil.isKeepAlive(req)) {
      val future: ChannelFuture = ctx.writeAndFlush(EntityTooLargeClose.retainedDuplicate())
      future.addListener(new ChannelFutureListener() {
        override def operationComplete(future: ChannelFuture) = {
          if (!future.isSuccess()) {
            logger.warn("Failed to send a 413 Request Entity Too Large.", future.cause())
          }
          ctx.close()
        }
      })
    } else {
      ctx
        .writeAndFlush(EntityTooLarge.retainedDuplicate())
        .addListener(new ChannelFutureListener() {
          override def operationComplete(future: ChannelFuture) = {
            if (!future.isSuccess()) {
              logger.warn("Failed to send a 413 Request Entity Too Large.", future.cause())
              ctx.close()
            }
          }
        })
    }
  }

  private implicit class RichServerNettyResponse(val r: ServerResponse[NettyResponse]) {
    def handle(
        ctx: ChannelHandlerContext,
        byteBufHandler: (ChannelPromise, ByteBuf) => Unit,
        chunkedStreamHandler: (ChannelPromise, ChunkedStream) => Unit,
        chunkedFileHandler: (ChannelPromise, ChunkedFile) => Unit,
        reactiveStreamHandler: (ChannelPromise, Publisher[HttpContent]) => Unit,
        noBodyHandler: () => Unit
    ): Unit = {
      r.body match {
        case Some(function) => {
          val values = function(ctx)

          values match {
            case r: ByteBufNettyResponseContent                                => byteBufHandler(r.channelPromise, r.byteBuf)
            case r: ChunkedStreamNettyResponseContent                          => chunkedStreamHandler(r.channelPromise, r.chunkedStream)
            case r: ChunkedFileNettyResponseContent                            => chunkedFileHandler(r.channelPromise, r.chunkedFile)
            case r: ReactivePublisherNettyResponseContent => reactiveStreamHandler(r.channelPromise, r.publisher)
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

    def handleContentLengthAndChunkedHeaders(length: Option[Long]): Unit = {
      val lengthKnownAndShouldBeSet = !m.headers().contains(HttpHeaderNames.CONTENT_LENGTH) && length.nonEmpty
      val lengthUnknownAndChunkedShouldBeUsed = !m.headers().contains(HttpHeaderNames.CONTENT_LENGTH) && length.isEmpty

      if (lengthKnownAndShouldBeSet) { length.map { l => m.headers().set(HttpHeaderNames.CONTENT_LENGTH, l) } }
      if (lengthUnknownAndChunkedShouldBeUsed) { m.headers().add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED) }
    }

    def handleCloseAndKeepAliveHeaders(request: HttpRequest): Unit = {
      if (!HttpUtil.isKeepAlive(request))
        m.headers.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
      else if (request.protocolVersion.equals(HttpVersion.HTTP_1_0))
        m.headers.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
    }
  }

  private implicit class RichChannelFuture(val cf: ChannelFuture) {
    def closeIfNeeded(request: HttpRequest): Unit = {
      if (!HttpUtil.isKeepAlive(request)) {
        cf.addListener(ChannelFutureListener.CLOSE)
      }
    }
  }
}
