package sttp.tapir.server.interceptor.reject

import sttp.model.StatusCode
import sttp.monad.MonadError
import sttp.tapir._
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.model.ValuedEndpointOutput

trait RejectHandler[F[_]] {
  def apply(ctx: RejectContext)(implicit monad: MonadError[F]): F[Option[ValuedEndpointOutput[?]]]
}

object RejectHandler {
  def apply[F[_]](f: RejectContext => F[Option[ValuedEndpointOutput[?]]]): RejectHandler[F] = new RejectHandler[F] {
    override def apply(ctx: RejectContext)(implicit monad: MonadError[F]): F[Option[ValuedEndpointOutput[?]]] =
      f(ctx)
  }

  def pure[F[_]](f: RejectContext => Option[ValuedEndpointOutput[?]]): RejectHandler[F] = new RejectHandler[F] {
    override def apply(ctx: RejectContext)(implicit monad: MonadError[F]): F[Option[ValuedEndpointOutput[?]]] =
      monad.unit(f(ctx))
  }
}

case class DefaultRejectHandler[F[_]](
    response: (StatusCode, String) => ValuedEndpointOutput[?],
    defaultStatusCodeAndBody: Option[(StatusCode, String)]
) extends RejectHandler[F] {
  override def apply(ctx: RejectContext)(implicit monad: MonadError[F]): F[Option[ValuedEndpointOutput[?]]] = {
    import DefaultRejectHandler._

    val statusCodeAndBody = if (hasMethodMismatch(ctx.failure)) Some(Responses.MethodNotAllowed) else defaultStatusCodeAndBody
    monad.unit(statusCodeAndBody.map(response.tupled))
  }
}

object DefaultRejectHandler {
  def apply[F[_]]: RejectHandler[F] =
    DefaultRejectHandler[F]((sc: StatusCode, m: String) => ValuedEndpointOutput(statusCode.and(stringBody), (sc, m)), None)

  def orNotFound[F[_]]: RejectHandler[F] =
    DefaultRejectHandler[F](
      (sc: StatusCode, m: String) => ValuedEndpointOutput(statusCode.and(stringBody), (sc, m)),
      Some(Responses.NotFound)
    )

  private def hasMethodMismatch(f: RequestResult.Failure): Boolean = f.failures.map(_.failingInput).exists {
    case _: EndpointInput.FixedMethod[?] => true
    case _                               => false
  }

  object Responses {
    val NotFound: (StatusCode, String) = (StatusCode.NotFound, "Not Found")
    val MethodNotAllowed: (StatusCode, String) = (StatusCode.MethodNotAllowed, "Method Not Allowed")
  }
}
