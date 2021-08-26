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
import io.netty.buffer.Unpooled
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

class NettyTestServerInterpreter(implicit ec: ExecutionContext)
    extends TestServerInterpreter[Future, Any, FullHttpRequest => Future[FullHttpResponse]] { //Any or NoStreams
  override def route[I, E, O](
      e: ServerEndpoint[I, E, O, Any, Future],
      decodeFailureHandler: Option[DecodeFailureHandler] = None,
      metricsInterceptor: Option[MetricsRequestInterceptor[Future]] = None
  ): (FullHttpRequest => Future[FullHttpResponse]) = {
    val serverOptions: NettyServerOptions = NettyServerOptions.customInterceptors
      .metricsInterceptor(metricsInterceptor)
      .decodeFailureHandler(decodeFailureHandler.getOrElse(DefaultDecodeFailureHandler.handler))
      .options

    NettyServerInterpreter(serverOptions).toHandler(List(e))
  }

  override def route[I, E, O](es: List[ServerEndpoint[I, E, O, Any, Future]]): FullHttpRequest => Future[FullHttpResponse] = {
    NettyServerInterpreter().toHandler(es)
  }

  override def routeRecoverErrors[I, E <: Throwable, O](e: Endpoint[I, E, O, Any], fn: I => Future[O])(implicit
      eClassTag: ClassTag[E]
  ): FullHttpRequest => Future[FullHttpResponse] = {
    //todo
    NettyServerInterpreter().toHandler(List(e.serverLogicRecoverErrors(fn)))
  }
  //resources!
  val acceptors = new NioEventLoopGroup()
  val workers = new NioEventLoopGroup()
  override def server(routes: NonEmptyList[FullHttpRequest => Future[FullHttpResponse]]): Resource[IO, Port] = {

    val bind = IO.fromFuture({
      //todo: concat routes

      class ServerHandler(val handlers: List[FullHttpRequest => Future[FullHttpResponse]])(implicit val ec: ExecutionContext)
          extends SimpleChannelInboundHandler[FullHttpRequest] {

        override def channelRead0(ctx: ChannelHandlerContext, req: FullHttpRequest): Unit = {
          if (HttpUtil.is100ContinueExpected(req)) {
            ctx.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE));
          } else {
            //hmm
            val req2 = req.copy().replace(Unpooled.copiedBuffer(req.content()))
            // if 404 -> next
            val xs: List[Future[FullHttpResponse]] = handlers.map(h => h(req2))

            val us = Future.find(xs)(resp => resp.status().code() != 404)

            us.map {
              case Some(value) =>
                flushResponse(ctx, req2, value)
              case None =>
                val res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(404))
                res.headers().set(HttpHeaderNames.CONTENT_LENGTH, res.content().readableBytes())
                flushResponse(ctx, req2, res)
            }
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

      class ServerInitializer(val handlers: List[FullHttpRequest => Future[FullHttpResponse]])(implicit val ec: ExecutionContext)
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
      // Configure the server
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
      .make(bind)(binding =>
        IO.fromFuture(IO {
          Future {
            binding.channel.closeFuture
            //todo
            //.sync
//            workers.shutdownGracefully()
//            acceptors.shutdownGracefully()
          }
        }).void
      )
      .map(x => {
        val ch = x.channel()
        x.channel().localAddress()
        ch.localAddress().toString.split(":").toList.last.toInt
      })
  }
}
