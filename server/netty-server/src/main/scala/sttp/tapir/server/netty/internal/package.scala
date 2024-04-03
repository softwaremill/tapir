package sttp.tapir.server.netty

import io.netty.channel.{ChannelFuture, ChannelFutureListener}
import io.netty.handler.codec.http.{HttpHeaderNames, HttpHeaders, HttpMessage}
import sttp.model.Header
import sttp.tapir.server.model.ServerResponse

import scala.collection.JavaConverters._

package object internal {
  implicit class RichNettyHttpHeaders(private val underlying: HttpHeaders) extends AnyVal {
    def toHeaderSeq: List[Header] =
      underlying.asScala.map(e => Header(e.getKey, e.getValue)).toList
  }

  implicit class RichChannelFuture(val cf: ChannelFuture) {
    def close(): Unit = {
      val _ = cf.addListener(ChannelFutureListener.CLOSE)
    }
  }

  implicit class RichHttpMessage(private val m: HttpMessage) extends AnyVal {
    def setHeadersFrom(response: ServerResponse[_], serverHeader: Option[String]): Unit = {
      serverHeader.foreach(m.headers().set(HttpHeaderNames.SERVER, _))
      response.headers
        .groupBy(_.name)
        .foreach { case (k, v) =>
          m.headers().set(k, v.map(_.value).asJava)
        }
    }
  }
  final val ServerCodecHandlerName = "serverCodecHandler"
  final val WebSocketControlFrameHandlerName = "wsControlFrameHandler"
}
