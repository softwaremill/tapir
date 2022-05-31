package sttp.tapir.server.netty.cats.internal

import cats.effect.Async
import io.netty.channel.{Channel, ChannelFuture}

import scala.concurrent.CancellationException

object CatsUtil {
  def nettyChannelFutureToScala[F[_]: Async](nettyFuture: ChannelFuture): F[Channel] = {
    Async[F].async { k =>
      nettyFuture.addListener((future: ChannelFuture) =>
        if (future.isSuccess) k(Right(future.channel()))
        else if (future.isCancelled) k(Left(new CancellationException))
        else k(Left(future.cause()))
      )

      Async[F].pure(None)
    }
  }

  def nettyFutureToScala[F[_]: Async, T](f: io.netty.util.concurrent.Future[T]): F[T] = {
    Async[F].async { k =>
      f.addListener((future: io.netty.util.concurrent.Future[T]) => {
        if (future.isSuccess) k(Right(future.getNow))
        else if (future.isCancelled) k(Left(new CancellationException))
        else k(Left(f.cause()))
      })

      Async[F].pure(None)
    }
  }
}
