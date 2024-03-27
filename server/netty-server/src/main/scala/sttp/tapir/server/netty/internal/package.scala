package sttp.tapir.server.netty

import io.netty.handler.codec.http.HttpHeaders
import sttp.model.Header

import scala.collection.JavaConverters._

package object internal {
  implicit class RichNettyHttpHeaders(underlying: HttpHeaders) {
    def toHeaderSeq: List[Header] =
      underlying.asScala.map(e => Header(e.getKey, e.getValue)).toList
  }

  val ServerCodecHandlerName = "serverCodecHandler"
  val WebSocketControlFrameHandlerName = "wsControlFrameHandler"
}
