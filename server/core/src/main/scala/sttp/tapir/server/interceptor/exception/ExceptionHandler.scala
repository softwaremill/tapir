package sttp.tapir.server.interceptor.exception

import sttp.model.StatusCode
import sttp.monad.MonadError
import sttp.tapir.server.model.ValuedEndpointOutput
import sttp.tapir._

trait ExceptionHandler[F[_]] {
  def apply(ctx: ExceptionContext)(implicit monad: MonadError[F]): F[Option[ValuedEndpointOutput[_]]]
}

object ExceptionHandler {
  def apply[F[_]](f: ExceptionContext => F[Option[ValuedEndpointOutput[_]]]): ExceptionHandler[F] =
    new ExceptionHandler[F] {
      override def apply(ctx: ExceptionContext)(implicit monad: MonadError[F]): F[Option[ValuedEndpointOutput[_]]] =
        f(ctx)
    }

  def pure[F[_]](f: ExceptionContext => Option[ValuedEndpointOutput[_]]): ExceptionHandler[F] =
    new ExceptionHandler[F] {
      override def apply(ctx: ExceptionContext)(implicit monad: MonadError[F]): F[Option[ValuedEndpointOutput[_]]] =
        monad.unit(f(ctx))
    }
}

case class DefaultExceptionHandler[F[_]](response: (StatusCode, String) => ValuedEndpointOutput[_]) extends ExceptionHandler[F] {
  override def apply(ctx: ExceptionContext)(implicit monad: MonadError[F]): F[Option[ValuedEndpointOutput[_]]] =
    monad.unit(Some(response(StatusCode.InternalServerError, "Internal server error")))
}

object DefaultExceptionHandler {
  def apply[F[_]]: ExceptionHandler[F] =
    DefaultExceptionHandler[F]((code: StatusCode, body: String) => ValuedEndpointOutput(statusCode.and(stringBody), (code, body)))
}
