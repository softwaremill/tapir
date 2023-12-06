package sttp.tapir.server.interceptor.exception

import sttp.capabilities.StreamMaxLengthExceededException
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
  override def apply(ctx: ExceptionContext)(implicit monad: MonadError[F]): F[Option[ValuedEndpointOutput[_]]] = {
    println("--------------------------------------------------------------------- WTF!")
    ctx.e match {
      case StreamMaxLengthExceededException(maxBytes) =>
        println(">>>>>>>>>> ayy lmao")
        monad.unit(Some(response(StatusCode.PayloadTooLarge, s"Payload limit (${maxBytes}B) exceeded")))
      // When dealing with streams, some libraries wrap stream failure exceptions into an additional layer,
      // which we need to unpack here.
      // For example, running a Pekko stream into a FileIO Sink wraps stream exceptions in IOOperationIncompleteException.
      case e: Exception if e.getCause().isInstanceOf[StreamMaxLengthExceededException] =>
        println(s">>>>>>>>>>>>>>>>>>>>>>>>>>> CAUSE! $e")
        val maxBytes = e.getCause().asInstanceOf[StreamMaxLengthExceededException].maxBytes
        monad.unit(Some(response(StatusCode.PayloadTooLarge, s"Payload limit (${maxBytes}B) exceeded")))
      case other =>
        println(s">>>>>>>>>>>>>>>>>>>>>>>>> Other :( $other)")
        monad.unit(Some(response(StatusCode.InternalServerError, "Internal server error")))
    }}
}

object DefaultExceptionHandler {
  def apply[F[_]]: ExceptionHandler[F] =
    DefaultExceptionHandler[F]((code: StatusCode, body: String) => ValuedEndpointOutput(statusCode.and(stringBody), (code, body)))
}
