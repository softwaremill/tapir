package sttp.tapir.server

import io.netty.channel.{Channel, ChannelFuture}
import io.netty.handler.codec.http.HttpHeaders
import sttp.model.Header

import scala.concurrent.{CancellationException, Future, Promise}
import scala.collection.JavaConverters._
import scala.util.{Failure, Success}

package object netty {
  private[netty] implicit class RichNettyHttpHeaders(underlying: HttpHeaders) {
    def toHeaderSeq: Seq[Header] =
      underlying.asScala.map(e => Header(e.getKey, e.getValue)).toSeq
  }

  private[netty] def nettyChannelFutureToScala(nettyFuture: ChannelFuture): Future[Channel] = {
    val p = Promise[Channel]()
    nettyFuture.addListener((future: ChannelFuture) =>
      p.complete(
        if (future.isSuccess) Success(future.channel())
        else if (future.isCancelled) Failure(new CancellationException)
        else Failure(future.cause())
      )
    )
    p.future
  }

  private[netty] def nettyFutureToScala[T](f: io.netty.util.concurrent.Future[T]): Future[T] = {
    val p = Promise[T]()
    f.addListener((future: io.netty.util.concurrent.Future[T]) => {
      if (future.isSuccess) p.complete(Success(future.getNow))
      else if (future.isCancelled) p.complete(Failure(new CancellationException))
      else p.complete(Failure(f.cause()))
    })
    p.future
  }
}
