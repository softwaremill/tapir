package sttp.tapir.server.armeria

import scala.util.{Failure, Success, Try}
import sttp.monad.syntax._
import sttp.monad.{Canceler, MonadAsyncError}
import sttp.tapir.server.interpreter.BodyListener

private[armeria] final class ArmeriaBodyListener[F[_]](implicit F: MonadAsyncError[F]) extends BodyListener[F, ArmeriaResponseType] {
  override def onComplete(body: ArmeriaResponseType)(cb: Try[Unit] => F[Unit]): F[ArmeriaResponseType] = {
    body match {
      case Left(stream) =>
        F.async[Try[Unit]] { cb0 =>
          stream
            .whenComplete()
            .handle[Unit] {
              case (_, null) =>
                cb0(Right(Success(())))
              case (_, ex) =>
                cb0(Right(Failure(ex)))
            }
          Canceler(() => stream.abort())
        }.flatMap(cb(_).map(_ => body))
      case Right(_) =>
        cb(Success(())).map(_ => body)
    }
  }
}
