package sttp.tapir.server.netty

import scala.collection.JavaConverters._
import scala.collection.immutable.Seq
import scala.concurrent.{CancellationException, Future, Promise}
import scala.util.{Failure, Success}

import io.netty.channel.{Channel, ChannelFuture}
import io.netty.handler.codec.http.HttpHeaders
import sttp.model.Header

package object internal {
  implicit class RichNettyHttpHeaders(underlying: HttpHeaders) {
    def toHeaderSeq: Seq[Header] =
      underlying.asScala.map(e => Header(e.getKey, e.getValue)).toList
  }
}
