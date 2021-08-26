package sttp.tapir.server.netty

import scala.concurrent.{ExecutionContext, Future}

import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.handler.codec.http._
import sttp.monad.FutureMonad
import sttp.tapir.internal.NoStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.interpreter.{BodyListener, ServerInterpreter}

import scala.jdk.CollectionConverters._

trait NettyServerInterpreter {
  def nettyServerOptions: NettyServerOptions = NettyServerOptions.default

  def toHandler(
      ses: List[ServerEndpoint[_, _, _, Any, Future]]
  )(implicit ec: ExecutionContext): FullHttpRequest => Future[FullHttpResponse] = { //Fail tapir response?
    val handler: FullHttpRequest => Future[FullHttpResponse] = { request: FullHttpRequest =>
      implicit val monad: FutureMonad = new FutureMonad()
      implicit val bodyListener: BodyListener[Future, ByteBuf] = new NettyBodyListener
      val serverRequest = new NettyServerRequest(request)
      val serverInterpreter = new ServerInterpreter[Any, Future, ByteBuf, NoStreams](
        new NettyRequestBody(request),
        new NettyToResponseBody,
        nettyServerOptions.interceptors,
        null //todo
      )

      serverInterpreter(serverRequest, ses)
        .map {
          case RequestResult.Response(response) => {
            val res = new DefaultFullHttpResponse(
              HttpVersion.valueOf(serverRequest.protocol),
              HttpResponseStatus.valueOf(response.code.code),
              response.body.getOrElse(Unpooled.EMPTY_BUFFER)
            )

            response.headers.groupBy(_.name).foreach { case (k, v) =>
              res.headers().set(k, v.map(_.value).toIterable.asJava) //important
            }
            res.headers().set(HttpHeaderNames.CONTENT_LENGTH, res.content().readableBytes())

            res
          }
          case RequestResult.Failure(f) => {
            //reject
            val res = new DefaultFullHttpResponse(HttpVersion.valueOf(serverRequest.protocol), HttpResponseStatus.valueOf(404))
            res.headers().set(HttpHeaderNames.CONTENT_LENGTH, res.content().readableBytes())
            res
          }
        }
        .recover { case e: Exception =>
          val res = new DefaultFullHttpResponse(HttpVersion.valueOf(serverRequest.protocol), HttpResponseStatus.valueOf(500))
          res.headers().set(HttpHeaderNames.CONTENT_LENGTH, res.content().readableBytes())
          res
        }
    }

    handler
  }
}
object NettyServerInterpreter {
  def apply(serverOptions: NettyServerOptions = NettyServerOptions.default): NettyServerInterpreter = {
    new NettyServerInterpreter {
      override def nettyServerOptions: NettyServerOptions = serverOptions
    }
  }
}
