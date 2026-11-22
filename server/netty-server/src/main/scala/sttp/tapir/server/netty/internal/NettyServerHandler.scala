package sttp.tapir.server.netty.internal

import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel._
import io.netty.channel.group.ChannelGroup
import io.netty.handler.codec.http._
import io.netty.handler.codec.http.websocketx.{WebSocketServerHandshakerFactory, WebSocketVersion}
import io.netty.handler.stream.{ChunkedFile, ChunkedStream}
import io.netty.handler.timeout.{IdleState, IdleStateEvent, IdleStateHandler}
import org.playframework.netty.http.{DefaultStreamedHttpResponse, DefaultWebSocketHttpResponse, StreamedHttpRequest}
import org.reactivestreams.{Publisher, Subscriber, Subscription}
import org.slf4j.LoggerFactory
import sttp.model.StatusCode
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
import sttp.tapir.server.netty.internal.reactivestreams.{CancellingSubscriber, TapirStreamedHttpRequest}
import sttp.tapir.server.netty.internal.ws.{WebSocketAutoPingHandler, WebSocketPingPongFrameHandler, WebSocketShutdownHandler}
import sttp.tapir.server.netty.{NettyConfig, NettyResponse, NettyServerRequest, Route}

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.JavaConverters._
import scala.collection.mutable.{Queue => MutableQueue}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

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
  // By using the Netty event loop assigned to this channel (see `handlerAdded`) we get two benefits:
  //  1. We can avoid the necessary hopping around of threads since Netty pipelines will
  //     only pass events up and down from within the event loop to which it is assigned.
  //     That means calls to ctx.read(), and ct.write(..), would have to be trampolined otherwise.
  //  2. We get serialization of execution: the EventLoop is a serial execution queue so
  //     we can rest easy knowing that no two events will be executed in parallel.
  private var eventLoopContext: ExecutionContext = null

  // This is used essentially as a queue, each incoming request attaches callbacks to this
  // and replaces it to ensure that responses are written out in the same order that they came
  // in.
  private var lastResponseSent: Future[Unit] = Future.unit

  // We keep track of the cancellation tokens for all the requests in flight. This gives us
  // observability into the number of requests in flight and the ability to cancel them all
  // if the connection gets closed.
  private val pendingResponses = MutableQueue.empty[() => Future[Unit]]

  // `IdleStateHandler` re-fires `WRITER_IDLE` every `requestTimeout` until a write completes; only the first firing is answered, as the
  // connection is closed along with the response. A plain var, as it's only touched on the event loop.
  private var requestTimeoutHandled = false

  // Requests received, for which the response isn't fully sent yet. When the server is shutting down, the connection is closed once this
  // drops to 0. A plain var, as it's only touched on the event loop.
  private var requestsInFlight = 0

  // Requests received, for which writing the response hasn't started yet. When the server is shutting down, only the last response
  // has `Connection: close`, so that the responses to pipelined requests are still sent.
  private var responsesNotStarted = 0

  // Present only if a request timeout is set, and the pipeline has the HTTP codec registered under `ServerCodecHandlerName`. If absent, an
  // exceeded request timeout is always reported as 503.
  private var requestBodyTracker: Option[RequestBodyCompletionTracker] = None

  private val logger = LoggerFactory.getLogger(getClass.getName)
  private final val WebSocketAutoPingHandlerName = "wsAutoPingHandler"
  private final val WebSocketShutdownHandlerName = "wsShutdownHandler"
  private final val RequestBodyTrackerHandlerName = "requestBodyTracker"

  override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
    // the handler's state, and that of the handlers it adds, is confined to the channel's event loop
    if (ctx.executor() ne ctx.channel.eventLoop) {
      throw new IllegalStateException(
        "NettyServerHandler must run on the channel's event loop: add it to the pipeline without an executor group"
      )
    }
    if (ctx.channel.isActive) {
      initHandler(ctx)
    }
  }
  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    channelGroup.add(ctx.channel)
    initHandler(ctx)
    // a connection accepted just before the server stopped listening might miss the ServerShuttingDown event
    if (isShuttingDown.get()) {
      val _ = ctx.close()
    }
  }

  private def requestCompleted(ctx: ChannelHandlerContext): Unit = {
    requestsInFlight -= 1
    // a removed handler means that the connection was upgraded to a web socket, which isn't closed here
    if (requestsInFlight == 0 && isShuttingDown.get() && !ctx.isRemoved) {
      val _ = ctx.close()
    }
  }

  private def initHandler(ctx: ChannelHandlerContext): Unit = {
    if (eventLoopContext == null) {
      eventLoopContext = ExecutionContext.fromExecutor(ctx.channel.eventLoop)
      config.idleTimeout.foreach { idleTimeout =>
        ctx.pipeline().addFirst(new IdleStateHandler(0, 0, idleTimeout.toMillis, TimeUnit.MILLISECONDS))
      }
      if (config.requestTimeout.isDefined && ctx.pipeline().context(ServerCodecHandlerName) != null) {
        val tracker = new RequestBodyCompletionTracker
        ctx.pipeline().addAfter(ServerCodecHandlerName, RequestBodyTrackerHandlerName, tracker)
        requestBodyTracker = Some(tracker)
      }
      // When the channel closes we want to cancel any pending dispatches. Not using `channelInactive`, as it's not propagated by the
      // `HandlerPublisher`, which netty-reactive-streams adds before this handler while a request body is streamed. The listener runs on
      // the channel's event loop.
      val _ = ctx.channel.closeFuture.addListener((_: ChannelFuture) => cancelPendingResponses(ctx))
    }
  }

  private def cancelPendingResponses(ctx: ChannelHandlerContext): Unit = {
    logger.debug("Http channel to {} closed. Cancelling {} responses.", ctx.channel.remoteAddress, pendingResponses.length)
    while (pendingResponses.nonEmpty) {
      pendingResponses.dequeue().apply(): Unit // running the cancellation for background side-effects only (best-effort)
    }
  }

  private def writeErrorThenClose(ctx: ChannelHandlerContext, status: HttpResponseStatus): Unit = {
    val res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status)
    res.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0)
    res.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
    val _ = ctx.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE)
  }

  private def handleRequestTimeout(ctx: ChannelHandlerContext): Unit = {
    val timeoutDescription = config.requestTimeout.map(_.toString).getOrElse("(not set)")
    // a started response, which isn't fully sent yet
    val writingResponse = requestsInFlight > responsesNotStarted
    if (writingResponse) {
      // an error response would be sent only after that response, which might never complete
      logger.error(s"Closing connection: no response produced within the request timeout of $timeoutDescription")
      val _ = ctx.close()
    } else if (requestBodyTracker.forall(t => t.bodyFullyReceived || t.continueAwaited)) {
      logger.error(s"Closing connection with 503: no response produced within the request timeout of $timeoutDescription")
      writeErrorThenClose(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE)
    } else {
      logger.debug(s"Closing connection with 408: request body not fully received within the request timeout of $timeoutDescription")
      writeErrorThenClose(ctx, HttpResponseStatus.REQUEST_TIMEOUT)
    }
  }

  override def userEventTriggered(ctx: ChannelHandlerContext, evt: Any): Unit = {
    evt match {
      case e: IdleStateEvent =>
        e.state() match {
          case IdleState.WRITER_IDLE if !requestTimeoutHandled =>
            requestTimeoutHandled = true
            handleRequestTimeout(ctx)
          case IdleState.ALL_IDLE =>
            logger.debug(s"Closing connection due to exceeded idle timeout of ${config.idleTimeout.map(_.toString).getOrElse("(not set)")}")
            val _ = ctx.close()
          case _ => ()
        }
      case NettyServerHandler.ServerShuttingDown =>
        if (requestsInFlight == 0) {
          logger.debug("Closing idle connection, as the server is shutting down")
          val _ = ctx.close()
        }
      case _ =>
        super.userEventTriggered(ctx, evt)
    }
  }

  override def channelRead0(ctx: ChannelHandlerContext, request: HttpRequest): Unit =
    if (isShuttingDown.get()) rejectRequest(ctx, request)
    else handleRequest(ctx, request)

  private def rejectRequest(ctx: ChannelHandlerContext, request: HttpRequest): Unit =
    if (requestsInFlight == 0) {
      logger.info("Rejecting request, server is shutting down")
      writeErrorThenClose(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE)
    } else {
      // A pipelined request: a 503 can't be written before the responses to the earlier requests. Instead, the request is dropped, and
      // the connection is closed once those responses are sent.
      logger.debug("Dropping a pipelined request, server is shutting down")
      request match {
        case r: StreamedHttpRequest => r.subscribe(new CancellingSubscriber)
        case _                      => // the body is released by SimpleChannelInboundHandler
      }
    }

  // `100 Continue` is left to HttpStreamsServerHandler, so that it's sent only if the body is, or might be, read, and clients don't upload
  // bodies that are then discarded (#5503, see TapirHttpStreamsServerHandler)
  private def handleRequest(ctx: ChannelHandlerContext, request: HttpRequest): Unit = request match {
    case full: FullHttpRequest =>
      val req = full.retain()
      runRoute(ctx, req, () => { val _ = req.release() })
    case req: TapirStreamedHttpRequest => runRoute(ctx, req)
    case req: StreamedHttpRequest      =>
      // from a custom pipeline with the plain `HttpStreamsServerHandler`: tracking the request body subscription status to discard it
      // safely, if it's never used; validating the body's length
      runRoute(ctx, new TapirStreamedHttpRequest(req))
    case _ => throw new UnsupportedOperationException(s"Unexpected Netty request type: ${request.getClass.getName}")
  }

  /** Runs the route, and writes its response after the responses to earlier requests on this connection, in request order. */
  private def runRoute(ctx: ChannelHandlerContext, req: HttpRequest, releaseReq: () => Unit = () => ()): Unit = {
    val requestTimeoutHandler = config.requestTimeout.map { requestTimeout =>
      new IdleStateHandler(0, requestTimeout.toMillis, 0, TimeUnit.MILLISECONDS)
    }
    requestTimeoutHandler.foreach(h => ctx.pipeline().addFirst(h))
    val (runningFuture, cancellationSwitch) = startRoute(ctx, req)
    pendingResponses.enqueue(cancellationSwitch)
    requestsInFlight += 1
    responsesNotStarted += 1
    // the failure of a previous response is already handled, and mustn't prevent sending later ones
    lastResponseSent = lastResponseSent.transformWith { _ =>
      runningFuture.transform { result =>
        responsesNotStarted -= 1
        val _ = pendingResponses.dequeueFirst(_ eq cancellationSwitch)
        def cleanUp(responseWritten: Option[ChannelFuture]): Unit = {
          releaseReq()
          afterResponseWritten(responseWritten) {
            requestCompleted(ctx)
            discardBodyIfUnused(req)
          }
        }
        val (responseWritten, outcome) =
          try writeResponse(ctx, req, result, requestTimeoutHandler)
          catch {
            case NonFatal(e)  => (None, Failure(e))
            case e: Throwable => cleanUp(None); throw e
          }
        cleanUp(responseWritten)
        outcome
      }(using eventLoopContext)
    }(using eventLoopContext)
  }

  /** @return
    *   the running response, and a function cancelling it. If the route can't be started, the response fails.
    */
  private def startRoute(ctx: ChannelHandlerContext, req: HttpRequest): (Future[ServerResponse[NettyResponse]], () => Future[Unit]) =
    try {
      unsafeRunAsync { () =>
        try route(NettyServerRequest(req, ctx)).map(_.getOrElse(ServerResponse.notFound))
        catch {
          case e: IllegalArgumentException if e.getMessage.startsWith("URLDecoder:") =>
            logger.debug(s"Invalid request URL: ${req.uri()}", e)
            me.unit(ServerResponse[NettyResponse](StatusCode.BadRequest, Nil, None, None))
        }
      }
    } catch {
      // e.g. when the effect's runtime is shut down, or when a route which is run eagerly throws: answered with a 500
      case NonFatal(e) => (Future.failed(e), () => Future.unit)
    }

  /** Writes the response, or a 500 if the route failed. If the channel is closed, the response is only released.
    *
    * @return
    *   completed once the response is written (`None` if nothing is written), and the outcome of handling the request
    */
  private def writeResponse(
      ctx: ChannelHandlerContext,
      req: HttpRequest,
      result: Try[ServerResponse[NettyResponse]],
      requestTimeoutHandler: Option[ChannelHandler]
  ): (Option[ChannelFuture], Try[Unit]) =
    // #4131: the channel might be closed if the request timed out. Both timeout & response-ready events are handled on the event loop, so
    // they won't be handled concurrently.
    if (ctx.channel().isOpen()) {
      requestTimeoutHandler.foreach(ctx.pipeline().remove)
      result.flatMap(serverResponse => Try(handleResponse(ctx, req, serverResponse))) match {
        case Success(written)     => (Some(written), Success(()))
        case Failure(NonFatal(e)) => (Some(writeError500(ctx, req, e)), Failure(e))
        case Failure(e)           => (None, Failure(e))
      }
    } else {
      result match {
        case Success(serverResponse) =>
          val e = new RuntimeException("Client disconnected, request timed out, or request cancelled")
          val released = handleResponseWhenChannelClosed(ctx, serverResponse, e)
          logger.debug("Response created but not sent, as the channel is closed (due to client disconnect, timeout, or cancellation).")
          (Some(released), Success(()))
        case Failure(e) => (None, Failure(e))
      }
    }

  private def writeError500(ctx: ChannelHandlerContext, req: HttpRequest, reason: Throwable): ChannelFuture = {
    logger.error("Error while processing the request", reason)
    val res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR)
    res.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0)
    res.handleCloseAndKeepAliveHeaders(req)
    val written = ctx.writeAndFlush(res)
    written.closeIfNeeded(req)
    written
  }

  private def afterResponseWritten(responseWritten: Option[ChannelFuture])(f: => Unit): Unit = responseWritten match {
    case Some(written) => val _ = written.addListener((_: ChannelFuture) => f)
    case None          => f
  }

  // #4539: a request body which was never subscribed to is discarded safely, by subscribing & immediately cancelling the subscription. This
  // is checked only once the response is written, as producing the response might use the request body.
  private def discardBodyIfUnused(req: HttpRequest): Unit = req match {
    case r: TapirStreamedHttpRequest if !r.wasSubscribed => r.subscribe(new CancellingSubscriber)
    case _                                               => // the body is already read in full, or used by the route
  }

  private def handleResponse(ctx: ChannelHandlerContext, req: HttpRequest, serverResponse: ServerResponse[NettyResponse]): ChannelPromise =
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
        def reject(res: FullHttpResponse): Unit = {
          res.headers().set(HttpHeaderNames.CONTENT_LENGTH, res.content().readableBytes())
          res.handleCloseAndKeepAliveHeaders(req)
          ctx.writeAndFlush(res, responseContent.channelPromise).closeIfNeeded(req)
        }
        val handshakerFactory = new WebSocketServerHandshakerFactory(wsUrl(req), null, false)
        if (!isWsHandshake(req)) {
          val buf = Unpooled.wrappedBuffer("Incorrect Web Socket handshake".getBytes)
          reject(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST, buf))
        } else if (handshakerFactory.newHandshaker(req) == null) {
          // an unsupported version; checked here, as otherwise HttpStreamsServerHandler responds once this handler is already removed
          val res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.UPGRADE_REQUIRED)
          res.headers().set(HttpHeaderNames.SEC_WEBSOCKET_VERSION, WebSocketVersion.V13.toHttpHeaderValue)
          reject(res)
        } else initWsPipeline(ctx, responseContent, req, handshakerFactory)
      },
      noBodyHandler = channelPromise => {
        val res = new DefaultFullHttpResponse(
          req.protocolVersion(),
          HttpResponseStatus.valueOf(serverResponse.code.code),
          Unpooled.EMPTY_BUFFER
        )

        res.setHeadersFrom(serverResponse)
        res.handleContentLengthAndChunkedHeaders(Option(Unpooled.EMPTY_BUFFER.readableBytes()))
        res.handleCloseAndKeepAliveHeaders(req)

        ctx.writeAndFlush(res, channelPromise).closeIfNeeded(req)
      }
    )

  private def handleResponseWhenChannelClosed(
      ctx: ChannelHandlerContext,
      serverResponse: ServerResponse[NettyResponse],
      channelPromiseFailure: Exception
  ): ChannelPromise =
    serverResponse.handle(
      ctx = ctx,
      byteBufHandler = (channelPromise, byteBuf) => { val _ = channelPromise.setFailure(channelPromiseFailure) },
      chunkedStreamHandler = (channelPromise, chunkedStream) => {
        chunkedStream.close()
        val _ = channelPromise.setFailure(channelPromiseFailure)
      },
      chunkedFileHandler = (channelPromise, chunkedFile) => {
        chunkedFile.close()
        val _ = channelPromise.setFailure(channelPromiseFailure)
      },
      reactiveStreamHandler = (channelPromise, publisher) => {
        publisher.subscribe(new Subscriber[HttpContent] {
          override def onSubscribe(s: Subscription): Unit = {
            s.cancel()
            val _ = channelPromise.setFailure(channelPromiseFailure)
          }
          override def onNext(t: HttpContent): Unit = ()
          override def onError(t: Throwable): Unit = ()
          override def onComplete(): Unit = ()
        })
      },
      wsHandler = (responseContent) => {
        val _ = responseContent.channelPromise.setFailure(channelPromiseFailure)
      },
      noBodyHandler = channelPromise => { val _ = channelPromise.setSuccess() }
    )

  private def initWsPipeline(
      ctx: ChannelHandlerContext,
      r: ReactiveWebSocketProcessorNettyResponseContent,
      handshakeReq: HttpRequest,
      handshakerFactory: WebSocketServerHandshakerFactory
  ) = {
    ctx.pipeline().remove(this)
    requestBodyTracker.foreach(ctx.pipeline().remove(_))
    ctx.pipeline().addAfter(ServerCodecHandlerName, WebSocketShutdownHandlerName, new WebSocketShutdownHandler(isShuttingDown))
    ctx
      .pipeline()
      .addAfter(
        WebSocketShutdownHandlerName,
        WebSocketControlFrameHandlerName,
        new WebSocketPingPongFrameHandler(
          ignorePong = r.ignorePong,
          autoPongOnPing = r.autoPongOnPing
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
        handshakerFactory
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

    /** @return
      *   a promise which is completed when the body is completely sent
      */
    def handle(
        ctx: ChannelHandlerContext,
        byteBufHandler: (ChannelPromise, ByteBuf) => Unit,
        chunkedStreamHandler: (ChannelPromise, ChunkedStream) => Unit,
        chunkedFileHandler: (ChannelPromise, ChunkedFile) => Unit,
        reactiveStreamHandler: (ChannelPromise, Publisher[HttpContent]) => Unit,
        wsHandler: ReactiveWebSocketProcessorNettyResponseContent => Unit,
        noBodyHandler: ChannelPromise => Unit
    ): ChannelPromise = {
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

          values.channelPromise
        }
        case None =>
          val channelPromise = ctx.newPromise()
          noBodyHandler(channelPromise)
          channelPromise
      }
    }
  }

  private implicit class RichHttpMessage(m: HttpMessage) {
    def setHeadersFrom(response: ServerResponse[?]): Unit = {
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
      if (!HttpUtil.isKeepAlive(request) || (isShuttingDown.get() && responsesNotStarted == 0))
        m.headers.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
      else if (request.protocolVersion.equals(HttpVersion.HTTP_1_0))
        m.headers.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
    }
  }

  private implicit class RichChannelFuture(cf: ChannelFuture) {
    def closeIfNeeded(request: HttpRequest): Unit = {
      if (!HttpUtil.isKeepAlive(request)) {
        cf.addListener(ChannelFutureListener.CLOSE)
      }
    }
  }
}

object NettyServerHandler {

  /** A user event, fired on all connections when the server starts shutting down. Idle HTTP connections are then closed, and web sockets
    * get a close frame.
    */
  case object ServerShuttingDown
}
