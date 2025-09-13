package sttp.tapir.server.interceptor.exception

import sttp.capabilities.StreamMaxLengthExceededException
import sttp.model.StatusCode
import sttp.monad.MonadError
import sttp.tapir.server.model.ValuedEndpointOutput
import sttp.tapir._

trait ExceptionHandler[F[_]] {
  def apply(ctx: ExceptionContext)(implicit monad: MonadError[F]): F[Option[ValuedEndpointOutput[?]]]
}

object ExceptionHandler {
  def apply[F[_]](f: ExceptionContext => F[Option[ValuedEndpointOutput[?]]]): ExceptionHandler[F] =
    new ExceptionHandler[F] {
      override def apply(ctx: ExceptionContext)(implicit monad: MonadError[F]): F[Option[ValuedEndpointOutput[?]]] =
        f(ctx)
    }

  def pure[F[_]](f: ExceptionContext => Option[ValuedEndpointOutput[?]]): ExceptionHandler[F] =
    new ExceptionHandler[F] {
      override def apply(ctx: ExceptionContext)(implicit monad: MonadError[F]): F[Option[ValuedEndpointOutput[?]]] =
        monad.unit(f(ctx))
    }
}

case class DefaultExceptionHandler[F[_]](response: (StatusCode, String) => ValuedEndpointOutput[?]) extends ExceptionHandler[F] {
  override def apply(ctx: ExceptionContext)(implicit monad: MonadError[F]): F[Option[ValuedEndpointOutput[?]]] =
    (ctx.e, ctx.e.getCause()) match {
      case (StreamMaxLengthExceededException(maxBytes), _) =>
        monad.unit(Some(response(StatusCode.PayloadTooLarge, s"Payload limit (${maxBytes}B) exceeded")))
      case (_, StreamMaxLengthExceededException(maxBytes)) =>
        monad.unit(Some(response(StatusCode.PayloadTooLarge, s"Payload limit (${maxBytes}B) exceeded")))
      case _ =>
        monad.unit(Some(response(StatusCode.InternalServerError, "Internal server error")))
    }
}

object DefaultExceptionHandler {
  def apply[F[_]]: ExceptionHandler[F] =
    DefaultExceptionHandler[F]((code: StatusCode, body: String) => ValuedEndpointOutput(statusCode.and(stringBody), (code, body)))
}
