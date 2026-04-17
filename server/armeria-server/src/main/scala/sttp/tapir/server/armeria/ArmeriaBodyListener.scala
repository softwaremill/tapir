package sttp.tapir.server.armeria

import scala.util.{Failure, Success, Try}
import sttp.monad.syntax._
import sttp.monad.{Canceler, MonadAsyncError}
import sttp.tapir.server.interpreter.BodyListener

private[armeria] final class ArmeriaBodyListener[F[_]](implicit F: MonadAsyncError[F], futureConversion: FutureConversion[F])
    extends BodyListener[F, ArmeriaResponseType] {
  override def onComplete(body: ArmeriaResponseType)(cb: Try[Unit] => F[Unit]): F[ArmeriaResponseType] = {
    body match {
      case Left(stream) =>
        stream
          .whenComplete()
          .handle[Unit] {
            case (_, null) =>
              futureConversion.to(cb(Success(()))): Unit // no way to log failures here
            case (_, ex) =>
              futureConversion.to(cb(Failure(ex))): Unit // no way to log failures here
          }

        F.unit(Left(stream))
      case Right(_) =>
        cb(Success(())).map(_ => body)
    }
  }
}
