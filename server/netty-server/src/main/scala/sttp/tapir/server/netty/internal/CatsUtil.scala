package sttp.tapir.server.netty.internal

import cats.effect.{Async, Sync}
import io.netty.channel.{Channel, ChannelFuture}
import sttp.monad.MonadError

import scala.concurrent.CancellationException

object CatsUtil {
  class CatsMonadError[F[_]](implicit F: Sync[F]) extends MonadError[F] {
    override def unit[T](t: T): F[T] = F.pure(t)
    override def map[T, T2](fa: F[T])(f: T => T2): F[T2] = F.map(fa)(f)
    override def flatMap[T, T2](fa: F[T])(f: T => F[T2]): F[T2] = F.flatMap(fa)(f)
    override def error[T](t: Throwable): F[T] = F.raiseError(t)
    override protected def handleWrappedError[T](rt: F[T])(h: PartialFunction[Throwable, F[T]]): F[T] =
      F.recoverWith(rt)(h)
    override def eval[T](t: => T): F[T] = F.delay(t)
    override def suspend[T](t: => F[T]): F[T] = F.defer(t)
    override def flatten[T](ffa: F[F[T]]): F[T] = F.flatten(ffa)
    override def ensure[T](f: F[T], e: => F[Unit]): F[T] = F.guaranteeCase(f)(_ => e)
  }

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
