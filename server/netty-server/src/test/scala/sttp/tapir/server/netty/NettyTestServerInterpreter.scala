package sttp.tapir.server.netty

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests.Port
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.{
  Channel,
  ChannelFutureListener,
  ChannelHandlerContext,
  ChannelInitializer,
  ChannelOption,
  SimpleChannelInboundHandler
}
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.{
  DefaultFullHttpResponse,
  FullHttpRequest,
  FullHttpResponse,
  HttpHeaderNames,
  HttpHeaderValues,
  HttpObjectAggregator,
  HttpRequest,
  HttpResponse,
  HttpResponseStatus,
  HttpServerCodec,
  HttpUtil,
  HttpVersion
}
import io.netty.handler.logging.LoggingHandler
import sttp.tapir.model.ServerResponse
import sttp.tapir.server.netty.NettyServerInterpreter.{NettyRoutingResult, RoutingFailureCode}
import scala.jdk.CollectionConverters._
class NettyTestServerInterpreter(implicit ec: ExecutionContext)
    extends TestServerInterpreter[Future, Any, FullHttpRequest => NettyRoutingResult] {
  val acceptors = new NioEventLoopGroup()
  val workers = new NioEventLoopGroup()

  override def route[I, E, O](
      e: ServerEndpoint[I, E, O, Any, Future],
      decodeFailureHandler: Option[DecodeFailureHandler] = None,
      metricsInterceptor: Option[MetricsRequestInterceptor[Future]] = None
  ): (FullHttpRequest => NettyRoutingResult) = {
    val serverOptions: NettyServerOptions = NettyServerOptions.customInterceptors
      .metricsInterceptor(metricsInterceptor)
      .decodeFailureHandler(decodeFailureHandler.getOrElse(DefaultDecodeFailureHandler.handler))
      .options

    NettyServerInterpreter(serverOptions).toHandler(List(e))
  }

  override def route[I, E, O](es: List[ServerEndpoint[I, E, O, Any, Future]]): FullHttpRequest => NettyRoutingResult = {
    NettyServerInterpreter().toHandler(es)
  }

  override def routeRecoverErrors[I, E <: Throwable, O](e: Endpoint[I, E, O, Any], fn: I => Future[O])(implicit
      eClassTag: ClassTag[E]
  ): FullHttpRequest => NettyRoutingResult = {
    //todo
    NettyServerInterpreter().toHandler(List(e.serverLogicRecoverErrors(fn)))
  }

  override def server(routes: NonEmptyList[FullHttpRequest => NettyRoutingResult]): Resource[IO, Port] = {

    val bind = IO.fromFuture({
      class ServerHandler(val handlers: List[FullHttpRequest => NettyRoutingResult])(implicit val ec: ExecutionContext)
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
            //todo: AbstractByteBuf.checkAccessible hack
            val accessibleRequest = req.copy().replace(Unpooled.copiedBuffer(req.content()))

            val responses: List[Future[FullHttpResponse]] = handlers
              .map(_.apply(accessibleRequest))
              .map(_.map(toHttpResponse(_, accessibleRequest)))

            Future
              .find(responses)(_.status().code() != RoutingFailureCode)
              .map(_.getOrElse(routingFailureResp))
              .foreach(flushResponse(ctx, accessibleRequest, _))
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

      class ServerInitializer(val handlers: List[FullHttpRequest => NettyRoutingResult])(implicit val ec: ExecutionContext)
          extends ChannelInitializer[Channel] {

        def initChannel(ch: Channel) {
          val pipeline = ch.pipeline()
          pipeline.addLast(new HttpServerCodec())
          pipeline.addLast(new HttpObjectAggregator(Integer.MAX_VALUE))
          pipeline.addLast(new ServerHandler(handlers))
          pipeline.addLast(new LoggingHandler())
        }
      }

      val httpBootstrap = new ServerBootstrap()

      httpBootstrap
        .group(acceptors, workers)
        .channel(classOf[NioServerSocketChannel])
        .childHandler(new ServerInitializer(routes.toList))
        .option[java.lang.Integer](ChannelOption.SO_BACKLOG, 128) //https://github.com/netty/netty/issues/1692
        .childOption[java.lang.Boolean](ChannelOption.SO_KEEPALIVE, true) // https://github.com/netty/netty/issues/1692

      // Bind and start to accept incoming connections.
      val httpChannel = httpBootstrap.bind(0).sync

      IO(Future(httpChannel))
    })

    Resource
      .make(bind)(binding => IO.fromFuture(IO(Future(binding.channel.closeFuture))).void)
      .map(channelFuture => {
        val ch = channelFuture.channel()
        ch.localAddress().toString.split(":").toList.last.toInt
      })
  }
}
