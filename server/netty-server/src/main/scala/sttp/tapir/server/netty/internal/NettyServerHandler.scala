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
import scala.collection.mutable.{Queue => MutableQueue}
import scala.concurrent.Future

class NettyServerHandler[F[_]](route: Route[F], unsafeRunAsync: (() => F[Unit]) => (() => Future[Unit]), maxContentLength: Option[Int])(
    implicit me: MonadError[F]
) extends SimpleChannelInboundHandler[HttpRequest] {

  // We keep track of the cancellation tokens for all the requests in flight. This gives us
  // observability into the number of requests in flight and the ability to cancel them all
  // if the connection gets closed.
  private[this] val pendingResponses = MutableQueue.empty[() => Future[Unit]]

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

  override def handlerAdded(ctx: ChannelHandlerContext): Unit =
    if (ctx.channel.isActive) {
      initHandler(ctx)
    }
  override def channelActive(ctx: ChannelHandlerContext): Unit = initHandler(ctx)

  override def channelReadComplete(ctx: ChannelHandlerContext): Unit = {
    logger.trace(s"channelReadComplete: ctx = $ctx")
    // The normal response to read complete is to issue another read,
    // but we only want to do that if there are no requests in flight,
    // this will effectively limit the number of in flight requests that
    // we'll handle by pushing back on the TCP stream, but it also ensures
    // we don't get in the way of the request body reactive streams,
    // which will be using channel read complete and read to implement
    // their own back pressure
    if (pendingResponses.isEmpty) {
      ctx.read()
    } else {
      // otherwise forward it, so that any handler publishers downstream
      // can handle it
      ctx.fireChannelReadComplete()
    }
    ()
  }

  private[this] def initHandler(ctx: ChannelHandlerContext): Unit =
    // When the channel closes we want to cancel any pending dispatches.
    // Since the listener will be executed from the channels EventLoop everything is thread safe.
    ctx.channel.closeFuture.addListener { (_: ChannelFuture) =>
      logger.debug(s"Http channel to ${ctx.channel.remoteAddress} closed. Cancelling ${pendingResponses.length} responses.")
      pendingResponses.foreach(_.apply())
    }

    // AUTO_READ is off, so need to do the first read explicitly.
    // this method is called when the channel is registered with the event loop,
    // so ctx.read is automatically safe here w/o needing an isRegistered().
    val _ = ctx.read()

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
          val cancellationSwitch: () => Future[Unit] = unsafeRunAsync { () =>
            runRoute(req)
              .ensure {
                me.eval {
                  pendingResponses.dequeue()
                  req.release()
                }
              }
          } // exceptions should be handled
          pendingResponses.enqueue(cancellationSwitch)
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
            case r: ByteBufNettyResponseContent           => byteBufHandler(r.channelPromise, r.byteBuf)
            case r: ChunkedStreamNettyResponseContent     => chunkedStreamHandler(r.channelPromise, r.chunkedStream)
            case r: ChunkedFileNettyResponseContent       => chunkedFileHandler(r.channelPromise, r.chunkedFile)
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
