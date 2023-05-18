package sttp.tapir.server.netty.cats.internal

import cats.effect.Async
import cats.syntax.all._
import io.netty.channel.{Channel, ChannelFuture}

import scala.concurrent.CancellationException
import cats.effect.kernel.Sync

object CatsUtil {
  def nettyChannelFutureToScala[F[_]: Async](nettyFuture: ChannelFuture): F[Channel] = {
    Async[F].async { k =>
      Sync[F].delay {
      nettyFuture.addListener((future: ChannelFuture) =>
        if (future.isSuccess) k(Right(future.channel()))
        else if (future.isCancelled) k(Left(new CancellationException))
        else k(Left(future.cause()))
      )

      Some(Sync[F].delay(nettyFuture.cancel(true)).void)
      }
    }
  }

  def nettyFutureToScala[F[_]: Async, T](f: io.netty.util.concurrent.Future[T]): F[T] = {
    Async[F].async { k =>
      Sync[F].delay {
      f.addListener((future: io.netty.util.concurrent.Future[T]) => {
        if (future.isSuccess) k(Right(future.getNow))
        else if (future.isCancelled) k(Left(new CancellationException))
        else k(Left(f.cause()))
      })

      Some(Sync[F].delay(f.cancel(true)).void)
      }
    }
  }
}
