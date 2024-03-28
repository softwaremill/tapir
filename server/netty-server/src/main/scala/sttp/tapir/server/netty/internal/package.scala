package sttp.tapir.server.netty

import io.netty.channel.{ChannelFuture, ChannelFutureListener}
import io.netty.handler.codec.http.HttpHeaders
import sttp.model.Header

import scala.collection.JavaConverters._

package object internal {
  implicit class RichNettyHttpHeaders(underlying: HttpHeaders) {
    def toHeaderSeq: List[Header] =
      underlying.asScala.map(e => Header(e.getKey, e.getValue)).toList
  }

  implicit class RichChannelFuture(val cf: ChannelFuture) {
    def close(): Unit = {
      val _ = cf.addListener(ChannelFutureListener.CLOSE)
    }
  }
  val ServerCodecHandlerName = "serverCodecHandler"
  val WebSocketControlFrameHandlerName = "wsControlFrameHandler"
}
