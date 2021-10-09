package sttp.tapir.server.netty

import io.netty.handler.codec.http.HttpHeaders
import sttp.model.Header

import scala.collection.JavaConverters._
import scala.collection.immutable.Seq

package object internal {
  implicit class RichNettyHttpHeaders(underlying: HttpHeaders) {
    def toHeaderSeq: Seq[Header] =
      underlying.asScala.map(e => Header(e.getKey, e.getValue)).toList
  }
}
