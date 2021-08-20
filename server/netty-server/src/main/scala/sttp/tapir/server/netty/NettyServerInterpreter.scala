package sttp.tapir.server.netty

import scala.concurrent.{ExecutionContext, Future}

import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.handler.codec.http._
import sttp.monad.FutureMonad
import sttp.tapir.internal.NoStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.interpreter.{BodyListener, ServerInterpreter}

object NettyServerInterpreter {
  def toHandler(
      ses: List[ServerEndpoint[_, _, _, Any, Future]]
  )(implicit ec: ExecutionContext): FullHttpRequest => Future[FullHttpResponse] = {
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
            HttpVersion.valueOf(serverRequest.protocol),
            HttpResponseStatus.valueOf(response.code.code),
            response.body.getOrElse(Unpooled.EMPTY_BUFFER)
          )

          response.headers.foreach(h => res.headers().set(h.name, h.value))
          res.headers().set(HttpHeaderNames.CONTENT_LENGTH, res.content().readableBytes())

          res
        }
        case RequestResult.Failure(_) => {
          val res = new DefaultFullHttpResponse(HttpVersion.valueOf(serverRequest.protocol), HttpResponseStatus.valueOf(404))
          res.headers().set(HttpHeaderNames.CONTENT_LENGTH, res.content().readableBytes())
          res
        }
      }
    }

    handler
  }
}
