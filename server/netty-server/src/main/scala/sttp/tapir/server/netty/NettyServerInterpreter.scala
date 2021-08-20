package sttp.tapir.server.netty

import scala.concurrent.Future
import scala.util.{Success, Try}

import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.{ByteBuf, Unpooled}
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
  HttpMethod,
  HttpObjectAggregator,
  HttpRequest,
  HttpResponse,
  HttpResponseStatus,
  HttpServerCodec,
  HttpUtil,
  HttpVersion
}
import io.netty.util.CharsetUtil
import sttp.model.Method
import sttp.monad.{FutureMonad, MonadError}
import sttp.tapir.internal.NoStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.interpreter.{BodyListener, ServerInterpreter}
import sttp.tapir.{Endpoint, endpoint, query, stringBody}
//todo
import scala.concurrent.ExecutionContext.Implicits.global

import sttp.monad.syntax._
class NettyBodyListener[Future[_]](implicit m: MonadError[Future]) extends BodyListener[Future, ByteBuf] {
  override def onComplete(body: ByteBuf)(cb: Try[Unit] => Future[Unit]): Future[ByteBuf] = {
    //todo
    cb(Success(())).map(_ => body)
  }
}

case class FinatraRoute(handler: FullHttpRequest => Future[ByteBuf], method: Method, path: String)

object NettyServerInterpreter {
  def toHandler(ses: List[ServerEndpoint[_, _, _, Any, Future]]): FullHttpRequest => Future[FullHttpResponse] = {
    val handler: FullHttpRequest => Future[FullHttpResponse] = { request: FullHttpRequest =>
      implicit val monad: FutureMonad = new FutureMonad()
      implicit val bodyListener: BodyListener[Future, ByteBuf] = new NettyBodyListener
      val serverRequest = new NettyServerRequest(request)
      val serverInterpreter = new ServerInterpreter[Any, Future, ByteBuf, NoStreams](
        new NettyRequestBody(request),
        new NettyToResponseBody,
        Nil, //todo
        null //todo
      )

      serverInterpreter(serverRequest, ses).map {
        case RequestResult.Response(response) => {
          val res = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.valueOf(response.code.code),
            response.body.getOrElse(Unpooled.EMPTY_BUFFER)
          )

          // todo headers
          res.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
          res.headers().set(HttpHeaderNames.CONTENT_LENGTH, res.content().readableBytes());

          res
        }
        case RequestResult.Failure(failures) => ???
      }
    }

    handler
  }
}

class ServerHandler extends SimpleChannelInboundHandler[FullHttpRequest] { // or HttpRequest?

  println("ServerHandler init")

  override def channelRead0(ctx: ChannelHandlerContext, req: FullHttpRequest): Unit = {
    if (HttpUtil.is100ContinueExpected(req)) {
      ctx.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE));
    } else {

      // todo: handler <-> interpreter connection doesn't work
//      val helloWorld: Endpoint[String, Unit, String, Any] =
//        endpoint.get.in("hello").in(query[String]("name")).out(stringBody)
//      val logic = List(helloWorld.serverLogic(name => Future.successful(Right(s"Hello, $name!"))))
//      val requestHandler = NettyServerInterpreter.toHandler(logic)
//      val resp = requestHandler(req)
//      resp.map(flushResponse(ctx, req, _))

      val respContent = "content for resp"

      val res = new DefaultFullHttpResponse(
        HttpVersion.HTTP_1_1,
        HttpResponseStatus.OK,
        Unpooled.copiedBuffer(respContent, CharsetUtil.UTF_8)
      )

      res.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
      res.headers().set(HttpHeaderNames.CONTENT_LENGTH, res.content().readableBytes());

      flushResponse(ctx, req, res)
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
class ServerInitializer extends ChannelInitializer[Channel] {

  def initChannel(ch: Channel) {
    val pipeline = ch.pipeline()
    pipeline.addLast(new HttpServerCodec())
    pipeline.addLast(new HttpObjectAggregator(Integer.MAX_VALUE)) // option?
    pipeline.addLast(new ServerHandler)
  }

}

object TestingInterpereter extends App {
  val helloWorld: Endpoint[String, Unit, String, Any] =
    endpoint.get.in("hello").in(query[String]("name")).out(stringBody)
  val helloPost: Endpoint[String, Unit, String, Any] =
    endpoint.post.in("hello").in(query[String]("name")).out(stringBody)
  val helloInt: Endpoint[Int, Unit, String, Any] =
    endpoint.get.in("hello").in(query[Int]("name")).out(stringBody)

  val helloHandler = (name: String) => Future.successful(Right(s"Hello, $name!"))

  val routes = Map(
    helloWorld -> helloHandler
  )

  val acceptors = new NioEventLoopGroup()
  val workers = new NioEventLoopGroup()
  Try {
    val httpBootstrap = new ServerBootstrap()
    // Configure the server
    httpBootstrap
      .group(acceptors, workers)
      .channel(classOf[NioServerSocketChannel])
      .childHandler(new ServerInitializer)
      .option[java.lang.Integer](ChannelOption.SO_BACKLOG, 128) //https://github.com/netty/netty/issues/1692
      .childOption[java.lang.Boolean](ChannelOption.SO_KEEPALIVE, true) // https://github.com/netty/netty/issues/1692

    // Bind and start to accept incoming connections.
    val httpChannel = httpBootstrap.bind(8080).sync

    // Wait until server socket is closed
    httpChannel.channel.closeFuture.sync
  }

  workers.shutdownGracefully()
  acceptors.shutdownGracefully()
}
