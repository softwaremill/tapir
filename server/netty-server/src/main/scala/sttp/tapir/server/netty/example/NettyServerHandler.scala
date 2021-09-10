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
import sttp.tapir.server.netty.NettyServerInterpreter.Route

class NettyServerHandler(val handlers: List[Route])(implicit val ec: ExecutionContext)
    extends SimpleChannelInboundHandler[FullHttpRequest] {

  private lazy val routingFailureResp = {
    val res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND)
    res.headers().set(HttpHeaderNames.CONTENT_LENGTH, res.content().readableBytes())
    res
  }

  private def toHttpResponse(interpreterResponse: ServerResponse[ByteBuf], req: FullHttpRequest): FullHttpResponse = {
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

    res.headers().set(HttpHeaderNames.CONTENT_LENGTH, res.content().readableBytes())

    res
  }

  override def channelRead0(ctx: ChannelHandlerContext, request: FullHttpRequest): Unit = {
    if (HttpUtil.is100ContinueExpected(request)) {
      ctx.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE));
    } else {
      val noRouteFound = Future.successful[Option[ServerResponse[ByteBuf]]](None)
      val req = request.retainedDuplicate()

      handlers
        .foldLeft(noRouteFound)((routingResult, nextHandler) => {
          routingResult.flatMap {
            case Some(success) => Future.successful(Some(success))
            case None          => nextHandler(req)
          }
        })
        .map(_.map(toHttpResponse(_, request)))
        .map(_.getOrElse(routingFailureResp))
        .map(flushResponse(ctx, request, _))
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
