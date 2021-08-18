package sttp.tapir.server

import io.netty.handler.codec.http.HttpHeaders
import sttp.model.Header
import scala.jdk.CollectionConverters._

package object netty {
  implicit class RichNettyHttpHeaders(underlying: HttpHeaders) {
    def toHeaderSeq: Seq[Header] =
      underlying.asScala.map(e => Header(e.getKey, e.getValue)).toSeq
  }
}
