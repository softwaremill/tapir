package sttp.tapir.server.armeria

import scala.util.{Success, Try}
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.server.interpreter.BodyListener

private[armeria] final class ArmeriaBodyListener[F[_]: MonadError] extends BodyListener[F, ArmeriaResponseType] {
  override def onComplete(body: ArmeriaResponseType)(cb: Try[Unit] => F[Unit]): F[ArmeriaResponseType] = {
    cb(Success(())).map(_ => body)
  }
}
