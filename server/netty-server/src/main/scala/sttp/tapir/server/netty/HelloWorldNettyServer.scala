package sttp.tapir.server.netty

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.{
  Channel,
  ChannelFutureListener,
  ChannelHandlerContext,
  ChannelInitializer,
  ChannelOption,
  SimpleChannelInboundHandler
}
import io.netty.channel.nio.NioEventLoopGroup
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
import sttp.tapir.{Endpoint, endpoint, query, stringBody}

object HelloWorldNettyServer extends App {
  implicit val ec = scala.concurrent.ExecutionContext.global

  val helloWorld: Endpoint[String, Unit, String, Any] =
    endpoint.get.in("hello").in(query[String]("name")).out(stringBody)
  val helloHandler: String => Future[Either[Unit, String]] = (name: String) => Future.successful(Right(s"Hello, $name!"))

  val helloWorld2: Endpoint[String, Unit, String, Any] =
    endpoint.get.in("hello2").in(stringBody).out(stringBody)
  val helloHandler2: String => Future[Either[Unit, String]] = (p: String) => Future.successful(Right(s"Another hello, $p!"))

  val helloWorld3: Endpoint[Unit, Unit, String, Any] =
    endpoint.post.in("hello3").out(stringBody)
  val helloHandler3: () => Future[Either[Unit, String]] = () => Future.successful[Either[Unit, String]](Right(s"Just to check body!"))

  val handler = NettyServerInterpreter().toHandler(
    List(
      helloWorld2.serverLogic(helloHandler2),
      helloWorld.serverLogic(helloHandler)
//      helloWorld3.serverLogic(helloHandler3)
    )
  )

  val acceptors = new NioEventLoopGroup()
  val workers = new NioEventLoopGroup()
  Try {
    val httpBootstrap = new ServerBootstrap()
    // Configure the server
    httpBootstrap
      .group(acceptors, workers)
      .channel(classOf[NioServerSocketChannel])
      .childHandler(new ServerInitializer(handler))
      .option[java.lang.Integer](ChannelOption.SO_BACKLOG, 128) //https://github.com/netty/netty/issues/1692
      .childOption[java.lang.Boolean](ChannelOption.SO_KEEPALIVE, true) // https://github.com/netty/netty/issues/1692

    // Bind and start to accept incoming connections.
    val httpChannel = httpBootstrap.bind(8080).sync

    // Wait until server socket is closed
    httpChannel.channel.closeFuture.sync
  }

  workers.shutdownGracefully()
  acceptors.shutdownGracefully()

  class ServerHandler(val handler: FullHttpRequest => Future[FullHttpResponse])(implicit val ec: ExecutionContext)
      extends SimpleChannelInboundHandler[FullHttpRequest] {

    override def channelRead0(ctx: ChannelHandlerContext, req: FullHttpRequest): Unit = {
      if (HttpUtil.is100ContinueExpected(req)) {
        ctx.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE));
      } else {
        // nie działa
//        val r2 = new BetterFullHttpMessage(req, req.content().nioBuffer())
        val req2 = req.copy().replace(Unpooled.copiedBuffer(req.content()))
//        val r2 = new BetterFullHttpMessage(req2, req2.content().nioBuffer())

        handler(req2).map(flushResponse(ctx, req2, _))

        // nie pomaga
        // val req2 = req.copy().replace(req.content().copy())
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

  class ServerInitializer(val handler: FullHttpRequest => Future[FullHttpResponse])(implicit val ec: ExecutionContext)
      extends ChannelInitializer[Channel] {

    def initChannel(ch: Channel) {
      val pipeline = ch.pipeline()
      pipeline.addLast(new HttpServerCodec())
      pipeline.addLast(new HttpObjectAggregator(Integer.MAX_VALUE))
      pipeline.addLast(new ServerHandler(handler))
    }
  }
}
