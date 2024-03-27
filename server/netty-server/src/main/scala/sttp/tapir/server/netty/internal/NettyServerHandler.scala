package sttp.tapir.server.netty.internal

import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel._
import io.netty.channel.group.ChannelGroup
import io.netty.handler.codec.http._
import io.netty.handler.stream.{ChunkedFile, ChunkedStream}
import org.playframework.netty.http.{DefaultStreamedHttpResponse, StreamedHttpRequest}
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
import sttp.tapir.server.netty.{NettyResponse, NettyServerRequest, Route}

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
    serverHeader: Option[String]
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

  override def channelRead0(ctx: ChannelHandlerContext, request: HttpRequest): Unit = {

    def writeError500(req: HttpRequest, reason: Throwable): Unit = {
      logger.error("Error while processing the request", reason)
      val res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR)
      res.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0)
      res.handleCloseAndKeepAliveHeaders(req)

      ctx.writeAndFlush(res).closeIfNeeded(req)
    }

    def writeError503(req: HttpRequest): Unit = {
      val res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.SERVICE_UNAVAILABLE)
      res.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0)
      res.handleCloseAndKeepAliveHeaders(req)
      ctx.writeAndFlush(res).closeIfNeeded(req)
    }

    def runRoute(req: HttpRequest, releaseReq: () => Any = () => ()): Unit = {
      val (runningFuture, cancellationSwitch) = unsafeRunAsync { () =>
        route(NettyServerRequest(req))
          .map {
            case Some(response) => response
            case None           => ServerResponse.notFound
          }
      }
      pendingResponses.enqueue(cancellationSwitch)
      lastResponseSent = lastResponseSent.flatMap { _ =>
        runningFuture.transform {
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
      writeError503(request)
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
      wsHandler = (channelPromise) => {
        logger.error(
          "Unexpected WebSocket processor response received in NettyServerHandler, it should be handled only in the ReactiveWebSocketHandler"
        )
        val res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR)
        res.headers.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
        val _ = ctx.writeAndFlush(res)
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

  private implicit class RichServerNettyResponse(val r: ServerResponse[NettyResponse]) {
    def handle(
        ctx: ChannelHandlerContext,
        byteBufHandler: (ChannelPromise, ByteBuf) => Unit,
        chunkedStreamHandler: (ChannelPromise, ChunkedStream) => Unit,
        chunkedFileHandler: (ChannelPromise, ChunkedFile) => Unit,
        reactiveStreamHandler: (ChannelPromise, Publisher[HttpContent]) => Unit,
        wsHandler: ChannelPromise => Unit,
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
            case r: ReactiveWebSocketProcessorNettyResponseContent => wsHandler(r.channelPromise)
          }
        }
        case None => noBodyHandler()
      }
    }
  }

  private implicit class RichHttpMessage(val m: HttpMessage) {
    def setHeadersFrom(response: ServerResponse[_]): Unit = {
      serverHeader.foreach(m.headers().set(HttpHeaderNames.SERVER, _))
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

  private implicit class RichChannelFuture(val cf: ChannelFuture) {
    def closeIfNeeded(request: HttpRequest): Unit = {
      if (!HttpUtil.isKeepAlive(request) || isShuttingDown.get()) {
        cf.addListener(ChannelFutureListener.CLOSE)
      }
    }
  }
}
