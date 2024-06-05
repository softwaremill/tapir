package sttp.tapir.server.netty.internal

import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel._
import io.netty.channel.group.ChannelGroup
import io.netty.handler.codec.http._
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory
import io.netty.handler.stream.{ChunkedFile, ChunkedStream}
import io.netty.handler.timeout.{IdleState, IdleStateEvent, IdleStateHandler}
import org.playframework.netty.http.{DefaultStreamedHttpResponse, DefaultWebSocketHttpResponse, StreamedHttpRequest}
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.server.model.ServerResponse
import sttp.tapir.server.netty.NettyResponseContent.{
  ByteBufNettyResponseContent,
  ChunkedFileNettyResponseContent,
  ChunkedStreamNettyResponseContent,
  ReactivePublisherNettyResponseContent,
  ReactiveWebSocketProcessorNettyResponseContent
}
import sttp.tapir.server.netty.internal.ws.{NettyControlFrameHandler, WebSocketAutoPingHandler}
import sttp.tapir.server.netty.{NettyConfig, NettyResponse, NettyServerRequest, Route}

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.JavaConverters._
import scala.collection.mutable.{Queue => MutableQueue}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

/** @param unsafeRunAsync
  *   Function which dispatches given effect to run asynchronously, returning its result as a Future, and function of type `() =>
  *   Future[Unit]` allowing cancellation of that Future. For example, this can be realized by
  *   `cats.effect.std.Dispatcher.unsafeToFutureCancelable`.
  */
class NettyServerHandler[F[_]](
    route: Route[F],
    unsafeRunAsync: (() => F[ServerResponse[NettyResponse]]) => (Future[ServerResponse[NettyResponse]], () => Future[Unit]),
    channelGroup: ChannelGroup,
    isShuttingDown: AtomicBoolean,
    config: NettyConfig
)(implicit
    me: MonadError[F]
) extends SimpleChannelInboundHandler[HttpRequest] {

  // Cancellation handling with eventLoopContext, lastResponseSent, and pendingResponses has been adapted
  // from http4s: https://github.com/http4s/http4s-netty/pull/396/files
  // By using the Netty event loop assigned to this channel we get two benefits:
  //  1. We can avoid the necessary hopping around of threads since Netty pipelines will
  //     only pass events up and down from within the event loop to which it is assigned.
  //     That means calls to ctx.read(), and ct.write(..), would have to be trampolined otherwise.
  //  2. We get serialization of execution: the EventLoop is a serial execution queue so
  //     we can rest easy knowing that no two events will be executed in parallel.
  private[this] var eventLoopContext: ExecutionContext = _

  // This is used essentially as a queue, each incoming request attaches callbacks to this
  // and replaces it to ensure that responses are written out in the same order that they came
  // in.
  private[this] var lastResponseSent: Future[Unit] = Future.unit

  // We keep track of the cancellation tokens for all the requests in flight. This gives us
  // observability into the number of requests in flight and the ability to cancel them all
  // if the connection gets closed.
  private[this] val pendingResponses = MutableQueue.empty[() => Future[Unit]]

  private val logger = LoggerFactory.getLogger(getClass.getName)
  private final val WebSocketAutoPingHandlerName = "wsAutoPingHandler"

  override def handlerAdded(ctx: ChannelHandlerContext): Unit =
    if (ctx.channel.isActive) {
      initHandler(ctx)
    }
  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    channelGroup.add(ctx.channel)
    initHandler(ctx)
  }

  private[this] def initHandler(ctx: ChannelHandlerContext): Unit = {
    if (eventLoopContext == null) {
      // Initialize our ExecutionContext
      eventLoopContext = ExecutionContext.fromExecutor(ctx.channel.eventLoop)
      config.idleTimeout.foreach { idleTimeout =>
        ctx.pipeline().addFirst(new IdleStateHandler(0, 0, idleTimeout.toMillis.toInt, TimeUnit.MILLISECONDS))
      }
      // When the channel closes we want to cancel any pending dispatches.
      // Since the listener will be executed from the channels EventLoop everything is thread safe.
      val _ = ctx.channel.closeFuture.addListener { (_: ChannelFuture) =>
        if (logger.isDebugEnabled) {
          logger.debug("Http channel to {} closed. Cancelling {} responses.", ctx.channel.remoteAddress, pendingResponses.length)
        }
        while (pendingResponses.nonEmpty) {
          pendingResponses.dequeue().apply()
        }
      }
    }
  }

  def writeError503ThenClose(ctx: ChannelHandlerContext): Unit = {
    val res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.SERVICE_UNAVAILABLE)
    res.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0)
    res.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
    val _ = ctx.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE)
  }

  override def userEventTriggered(ctx: ChannelHandlerContext, evt: Any): Unit = {
    evt match {
      case e: IdleStateEvent =>
        if (e.state() == IdleState.WRITER_IDLE) {
          logger.error(s"Closing connection due to exceeded response timeout of ${config.requestTimeout}")
          writeError503ThenClose(ctx)
        }
        if (e.state() == IdleState.ALL_IDLE) {
          logger.debug(s"Closing connection due to exceeded idle timeout of ${config.idleTimeout}")
          val _ = ctx.close()
        }
      case other =>
        super.userEventTriggered(ctx, evt)
    }
  }

  override def channelRead0(ctx: ChannelHandlerContext, request: HttpRequest): Unit = {

    def writeError500(req: HttpRequest, reason: Throwable): Unit = {
      logger.error("Error while processing the request", reason)
      val res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR)
      res.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0)
      res.handleCloseAndKeepAliveHeaders(req)

      ctx.writeAndFlush(res).closeIfNeeded(req)
    }

    def runRoute(req: HttpRequest, releaseReq: () => Any = () => ()): Unit = {
      val requestTimeoutHandler = config.requestTimeout.map { requestTimeout =>
        new IdleStateHandler(0, requestTimeout.toMillis.toInt, 0, TimeUnit.MILLISECONDS)
      }
      requestTimeoutHandler.foreach(h => ctx.pipeline().addFirst(h))
      val (runningFuture, cancellationSwitch) = unsafeRunAsync { () =>
        route(NettyServerRequest(req))
          .map {
            case Some(response) => response
            case None           => ServerResponse.notFound
          }
      }
      pendingResponses.enqueue(cancellationSwitch)
      lastResponseSent = lastResponseSent.flatMap { _ =>
        runningFuture
          .andThen { case _ =>
            requestTimeoutHandler.foreach(ctx.pipeline().remove)
          }(eventLoopContext)
          .transform {
            case Success(serverResponse) =>
              pendingResponses.dequeue()
              try {
                handleResponse(ctx, req, serverResponse)
                Success(())
              } catch {
                case NonFatal(ex) =>
                  writeError500(req, ex)
                  Failure(ex)
              } finally {
                val _ = releaseReq()
              }
            case Failure(NonFatal(ex)) =>
              try {
                writeError500(req, ex)
                Failure(ex)
              } finally {
                val _ = releaseReq()
              }
            case Failure(fatalException) => Failure(fatalException)
          }(eventLoopContext)
      }(eventLoopContext)
    }

    if (isShuttingDown.get()) {
      logger.info("Rejecting request, server is shutting down")
      writeError503ThenClose(ctx)
    } else if (HttpUtil.is100ContinueExpected(request)) {
      ctx.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE))
      ()
    } else {
      request match {
        case full: FullHttpRequest =>
          val req = full.retain()
          runRoute(req, () => req.release())
        case req: StreamedHttpRequest =>
          runRoute(req)
        case _ => throw new UnsupportedOperationException(s"Unexpected Netty request type: ${request.getClass.getName}")
      }

      ()
    }
  }

  private def handleResponse(ctx: ChannelHandlerContext, req: HttpRequest, serverResponse: ServerResponse[NettyResponse]): Unit =
    serverResponse.handle(
      ctx = ctx,
      byteBufHandler = (channelPromise, byteBuf) => {
        val res = new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.valueOf(serverResponse.code.code), byteBuf)
        res.setHeadersFrom(serverResponse)
        res.handleContentLengthAndChunkedHeaders(Option(byteBuf.readableBytes()))
        res.handleCloseAndKeepAliveHeaders(req)
        ctx.writeAndFlush(res, channelPromise).closeIfNeeded(req)
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

        channelPromise.addListener((future: ChannelFuture) => {
          // A reactive publisher silently closes the channel and fails the channel promise, so we need
          // to listen on it and log failure details
          if (!future.isSuccess()) {
            logger.error("Error when streaming HTTP response", future.cause())
          }
        })
        ctx.writeAndFlush(res, channelPromise).closeIfNeeded(req)

      },
      wsHandler = (responseContent) => {
        if (isWsHandshake(req))
          initWsPipeline(ctx, responseContent, req)
        else {
          val buf = Unpooled.wrappedBuffer("Incorrect Web Socket handshake".getBytes)
          val res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST, buf)
          res.headers().set(HttpHeaderNames.CONTENT_LENGTH, buf.readableBytes())
          res.handleCloseAndKeepAliveHeaders(req)
          ctx.writeAndFlush(res).closeIfNeeded(req)
        }
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

  private def initWsPipeline(
      ctx: ChannelHandlerContext,
      r: ReactiveWebSocketProcessorNettyResponseContent,
      handshakeReq: HttpRequest
  ) = {
    ctx.pipeline().remove(this)
    ctx
      .pipeline()
      .addAfter(
        ServerCodecHandlerName,
        WebSocketControlFrameHandlerName,
        new NettyControlFrameHandler(
          ignorePong = r.ignorePong,
          autoPongOnPing = r.autoPongOnPing,
          decodeCloseRequests = r.decodeCloseRequests
        )
      )
    r.autoPing.foreach { case (interval, pingMsg) =>
      ctx
        .pipeline()
        .addAfter(
          WebSocketControlFrameHandlerName,
          WebSocketAutoPingHandlerName,
          new WebSocketAutoPingHandler(interval, pingMsg)
        )
    }
    // Manually completing the promise, for some reason it won't be completed in writeAndFlush. We need its completion for NettyBodyListener to call back properly
    r.channelPromise.setSuccess()
    val _ = ctx.writeAndFlush(
      // Push a special message down the pipeline, it will be handled by HttpStreamsServerHandler
      // and from now on that handler will take control of the flow (our NettyServerHandler will not receive messages)
      new DefaultWebSocketHttpResponse(
        handshakeReq.protocolVersion(),
        HttpResponseStatus.valueOf(200),
        r.processor, // the Processor (Pipe) created by Tapir interpreter will be used by HttpStreamsServerHandler
        new WebSocketServerHandshakerFactory(wsUrl(handshakeReq), null, false)
      )
    )
  }

  private def isWsHandshake(req: HttpRequest): Boolean =
    "Websocket".equalsIgnoreCase(req.headers().get(HttpHeaderNames.UPGRADE)) &&
      "Upgrade".equalsIgnoreCase(req.headers().get(HttpHeaderNames.CONNECTION))

  // Only ancient WS protocol versions will use this in the response header.
  private def wsUrl(req: HttpRequest): String = {
    val scheme = if (config.isSsl) "wss" else "ws"
    s"$scheme://${req.headers().get(HttpHeaderNames.HOST)}${req.uri()}"
  }
  private implicit class RichServerNettyResponse(r: ServerResponse[NettyResponse]) {
    def handle(
        ctx: ChannelHandlerContext,
        byteBufHandler: (ChannelPromise, ByteBuf) => Unit,
        chunkedStreamHandler: (ChannelPromise, ChunkedStream) => Unit,
        chunkedFileHandler: (ChannelPromise, ChunkedFile) => Unit,
        reactiveStreamHandler: (ChannelPromise, Publisher[HttpContent]) => Unit,
        wsHandler: ReactiveWebSocketProcessorNettyResponseContent => Unit,
        noBodyHandler: () => Unit
    ): Unit = {
      r.body match {
        case Some(function) => {
          val values = function(ctx)

          values match {
            case r: ByteBufNettyResponseContent                    => byteBufHandler(r.channelPromise, r.byteBuf)
            case r: ChunkedStreamNettyResponseContent              => chunkedStreamHandler(r.channelPromise, r.chunkedStream)
            case r: ChunkedFileNettyResponseContent                => chunkedFileHandler(r.channelPromise, r.chunkedFile)
            case r: ReactivePublisherNettyResponseContent          => reactiveStreamHandler(r.channelPromise, r.publisher)
            case r: ReactiveWebSocketProcessorNettyResponseContent => wsHandler(r)
          }
        }
        case None => noBodyHandler()
      }
    }
  }

  private implicit class RichHttpMessage(m: HttpMessage) {
    def setHeadersFrom(response: ServerResponse[_]): Unit = {
      config.serverHeader.foreach(m.headers().set(HttpHeaderNames.SERVER, _))
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
      if (!HttpUtil.isKeepAlive(request) || isShuttingDown.get())
        m.headers.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
      else if (request.protocolVersion.equals(HttpVersion.HTTP_1_0))
        m.headers.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
    }
  }

  private implicit class RichChannelFuture(cf: ChannelFuture) {
    def closeIfNeeded(request: HttpRequest): Unit = {
      if (!HttpUtil.isKeepAlive(request) || isShuttingDown.get()) {
        cf.addListener(ChannelFutureListener.CLOSE)
      }
    }
  }
}
