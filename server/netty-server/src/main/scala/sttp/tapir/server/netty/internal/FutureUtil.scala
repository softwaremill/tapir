package sttp.tapir.server.netty.internal

import io.netty.channel.{Channel, ChannelFuture}

import scala.concurrent.{CancellationException, Future, Promise}
import scala.util.{Failure, Success}

object FutureUtil {
  def nettyChannelFutureToScala(nettyFuture: ChannelFuture): Future[Channel] = {
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

  def nettyFutureToScala[T](f: io.netty.util.concurrent.Future[T]): Future[T] = {
    val p = Promise[T]()
    f.addListener((future: io.netty.util.concurrent.Future[T]) => {
      if (future.isSuccess) p.complete(Success(future.getNow))
      else if (future.isCancelled) p.complete(Failure(new CancellationException))
      else p.complete(Failure(f.cause()))
    })
    p.future
  }
}
