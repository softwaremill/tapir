package sttp.tapir.server.netty.internal.ws

import io.netty.buffer.Unpooled
import io.netty.channel.group.ChannelGroup
import io.netty.channel.{ChannelFuture, ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http._
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.util.ReferenceCountUtil
import org.playframework.netty.http.DefaultWebSocketHttpResponse
import org.slf4j.LoggerFactory
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.server.model.ServerResponse
import sttp.tapir.server.netty.NettyResponseContent.ReactiveWebSocketProcessorNettyResponseContent
import sttp.tapir.server.netty.internal._
import sttp.tapir.server.netty.{NettyResponse, NettyResponseContent, NettyServerRequest, Route}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}
import sttp.tapir.server.netty.NettyResponseContent.ByteBufNettyResponseContent
import io.netty.channel.ChannelPromise
import io.netty.buffer.ByteBuf
import java.util.concurrent.atomic.AtomicBoolean

/** Handles a WS handshake and initiates the communication by calling Tapir interpreter to get a Pipe, then sends that Pipe to the rest of
  * the processing pipeline and removes itself from the pipeline.
  */
class ReactiveWebSocketHandler[F[_]](
    route: Route[F],
    channelGroup: ChannelGroup,
    unsafeRunAsync: (() => F[ServerResponse[NettyResponse]]) => (Future[ServerResponse[NettyResponse]], () => Future[Unit]),
    isSsl: Boolean,
    isShuttingDown: AtomicBoolean,
    serverHeader: Option[String]
)(implicit m: MonadError[F])
    extends ChannelInboundHandlerAdapter {

  // By using the Netty event loop assigned to this channel we get two benefits:
  //  1. We can avoid the necessary hopping around of threads since Netty pipelines will
  //     only pass events up and down from within the event loop to which it is assigned.
  //     That means calls to ctx.read(), and ctx.write(..), would have to be trampolined otherwise.
  //  2. We get serialization of execution: the EventLoop is a serial execution queue so
  //     we can rest easy knowing that no two events will be executed in parallel.
  private[this] var eventLoopContext: ExecutionContext = _

  private val logger = LoggerFactory.getLogger(getClass.getName)
  private val WebSocketAutoPingHandlerName = "wsAutoPingHandler"

  def isWsHandshake(req: HttpRequest): Boolean =
    "Websocket".equalsIgnoreCase(req.headers().get(HttpHeaderNames.UPGRADE)) &&
      "Upgrade".equalsIgnoreCase(req.headers().get(HttpHeaderNames.CONNECTION))

  override def handlerAdded(ctx: ChannelHandlerContext): Unit =
    if (ctx.channel.isActive) {
      initHandler(ctx)
    }
  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    channelGroup.add(ctx.channel)
    initHandler(ctx)
  }

  private[this] def initHandler(ctx: ChannelHandlerContext): Unit = {
    if (eventLoopContext == null)
      eventLoopContext = ExecutionContext.fromExecutor(ctx.channel.eventLoop)
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    logger.error("Error while processing the request", cause)
  }

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    def replyWithError500(reason: Throwable): Unit = {
      logger.error("Error while processing the request", reason)
      val res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR)
      res.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0)
      res.headers.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
      val _ = ctx.writeAndFlush(res).close()
    }

    def replyWith503(): Unit = {
      val res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.SERVICE_UNAVAILABLE)
      res.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0)
      res.headers.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
      val _ = ctx.writeAndFlush(res).close()
    }

    def replyNotFound(): Unit = {
      val res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND)
      res.headers.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
      val _ = ctx.writeAndFlush(res).close()
    }

    def replyWithRouteResponse(
        req: HttpRequest,
        serverResponse: ServerResponse[NettyResponse],
        channelPromise: ChannelPromise,
        byteBuf: ByteBuf
    ): Unit = {
      val res = new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.valueOf(serverResponse.code.code), byteBuf)
      res.setHeadersFrom(serverResponse, serverHeader)
      res.headers().set(HttpHeaderNames.CONTENT_LENGTH, byteBuf.readableBytes())
      val _ = ctx.writeAndFlush(res, channelPromise)
    }

    msg match {
      case req: FullHttpRequest if isWsHandshake(req) =>
        if (isShuttingDown.get()) {
          logger.info("Rejecting WS handshake request because the server is shutting down.")
          replyWith503()
        } else {
          ReferenceCountUtil.release(msg)
          val (runningFuture, _) = unsafeRunAsync { () =>
            route(NettyServerRequest(req.retain()))
              .map {
                case Some(response) => response
                case None           => ServerResponse.notFound
              }
          }

          runningFuture.onComplete {
            case Success(serverResponse) if serverResponse == ServerResponse.notFound =>
              replyNotFound()
            case Success(serverResponse) =>
              try {
                serverResponse.body match {
                  case Some(function) => {
                    val content = function(ctx)
                    content match {
                      case r: ReactiveWebSocketProcessorNettyResponseContent =>
                        initWsPipeline(ctx, r, req)
                      case ByteBufNettyResponseContent(channelPromise, byteBuf) =>
                        // Handshake didn't return a Pipe, but a regular response. Returning it back.
                        replyWithRouteResponse(req, serverResponse, channelPromise, byteBuf)
                      case otherContent =>
                        // An unsupported type of regular response, returning 500
                        replyWithError500(
                          new IllegalArgumentException(s"Unsupported response type for a WS endpoint: ${otherContent.getClass.getName}")
                        )
                    }
                  }
                  case None =>
                    // Handshake didn't return a Pipe, but a regular response with empty body. Returning it back.
                    replyWithRouteResponse(req, serverResponse, ctx.newPromise(), Unpooled.EMPTY_BUFFER)
                }
              } catch {
                case NonFatal(ex) =>
                  replyWithError500(ex)
              } finally {
                val _ = req.release()
              }
            case Failure(ex) =>
              try {
                replyWithError500(ex)
              } finally {
                val _ = req.release()
              }
          }(eventLoopContext)
        }
      case other =>
        // not a WS handshake
        val _ = ctx.fireChannelRead(other)
    }
  }

  private def initWsPipeline(
      ctx: ChannelHandlerContext,
      r: ReactiveWebSocketProcessorNettyResponseContent,
      handshakeReq: FullHttpRequest
  ) = {
    ctx.pipeline().remove(this)
    ctx.pipeline().remove(classOf[ReadTimeoutHandler])
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

  // Only ancient WS protocol versions will use this in the response header.
  private def wsUrl(req: FullHttpRequest): String = {
    val scheme = if (isSsl) "wss" else "ws"
    s"$scheme://${req.headers().get(HttpHeaderNames.HOST)}${req.uri()}"
  }
}
