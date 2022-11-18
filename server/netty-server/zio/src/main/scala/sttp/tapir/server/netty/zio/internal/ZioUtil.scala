package sttp.tapir.server.netty.zio.internal

import io.netty.channel.{Channel, ChannelFuture}
import sttp.tapir.server.netty.internal.FutureUtil
import zio.{RIO, ZIO}

private[zio] object ZioUtil {
  def nettyChannelFutureToScala[R](nettyFuture: ChannelFuture): RIO[R, Channel] =
    ZIO.fromFuture(_ => FutureUtil.nettyChannelFutureToScala(nettyFuture))

  def nettyFutureToScala[R, T](f: io.netty.util.concurrent.Future[T]): RIO[R, T] =
    ZIO.fromFuture(_ => FutureUtil.nettyFutureToScala(f))
}
