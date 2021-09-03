package sttp.tapir.server.netty.example

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel.{ChannelFutureListener, ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.{
  DefaultFullHttpResponse,
  FullHttpRequest,
  FullHttpResponse,
  HttpHeaderNames,
  HttpHeaderValues,
  HttpRequest,
  HttpResponse,
  HttpResponseStatus,
  HttpUtil,
  HttpVersion
}
import sttp.tapir.model.ServerResponse
import sttp.tapir.server.netty.NettyServerInterpreter
import sttp.tapir.server.netty.NettyServerInterpreter.NettyRoutingResult

class NettyServerHandler(val handlers: List[FullHttpRequest => NettyRoutingResult])(implicit val ec: ExecutionContext)
    extends SimpleChannelInboundHandler[FullHttpRequest] {

  private lazy val routingFailureResp = {
    val res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND)
    res.headers().set(HttpHeaderNames.CONTENT_LENGTH, res.content().readableBytes())
    res
  }

  private def toHttpResponse(serverResult: Either[Int, ServerResponse[ByteBuf]], req: FullHttpRequest) = {
    val resp = serverResult match {
      case Left(httpCode) =>
        val error = new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.valueOf(httpCode))
        error.headers().set(HttpHeaderNames.CONTENT_LENGTH, error.content().readableBytes())
        error

      case Right(interpreterResponse) =>
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

        res
    }

    resp.headers().set(HttpHeaderNames.CONTENT_LENGTH, resp.content().readableBytes())
    resp
  }

  override def channelRead0(ctx: ChannelHandlerContext, req: FullHttpRequest): Unit = {
    if (HttpUtil.is100ContinueExpected(req)) {
      ctx.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE));
    } else {
      val responses: List[Future[FullHttpResponse]] = handlers
        .map(_.apply(req.retainedDuplicate()))
        .map(_.map(toHttpResponse(_, req)))

      Future
        .find(responses)(_.status().code() != NettyServerInterpreter.RoutingFailureCode)
        .map(_.getOrElse(routingFailureResp))
        .map(flushResponse(ctx, req, _))
    }
  }

  def flushResponse(ctx: ChannelHandlerContext, req: HttpRequest, res: HttpResponse): Unit = {
    if (!HttpUtil.isKeepAlive(req)) {
      ctx.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE)
    } else {
      res.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
      ctx.writeAndFlush(res)
    }
  }
}
