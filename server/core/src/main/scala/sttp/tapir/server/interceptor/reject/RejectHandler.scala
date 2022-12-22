package sttp.tapir.server.interceptor.reject

import sttp.model.StatusCode
import sttp.monad.MonadError
import sttp.tapir._
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.model.ValuedEndpointOutput

trait RejectHandler[F[_]] {
  def apply(failure: RequestResult.Failure)(implicit monad: MonadError[F]): F[Option[ValuedEndpointOutput[_]]]
}

object RejectHandler {
  def apply[F[_]](f: RequestResult.Failure => F[Option[ValuedEndpointOutput[_]]]): RejectHandler[F] = new RejectHandler[F] {
    override def apply(failure: RequestResult.Failure)(implicit monad: MonadError[F]): F[Option[ValuedEndpointOutput[_]]] =
      f(failure)
  }

  def pure[F[_]](f: RequestResult.Failure => Option[ValuedEndpointOutput[_]]): RejectHandler[F] = new RejectHandler[F] {
    override def apply(failure: RequestResult.Failure)(implicit monad: MonadError[F]): F[Option[ValuedEndpointOutput[_]]] =
      monad.unit(f(failure))
  }
}

case class DefaultRejectHandler[F[_]](
    response: (StatusCode, String) => ValuedEndpointOutput[_],
    defaultStatusCodeAndBody: Option[(StatusCode, String)]
) extends RejectHandler[F] {
  override def apply(failure: RequestResult.Failure)(implicit monad: MonadError[F]): F[Option[ValuedEndpointOutput[_]]] = {
    import DefaultRejectHandler._

    val statusCodeAndBody = if (hasMethodMismatch(failure)) Some(Responses.MethodNotAllowed) else defaultStatusCodeAndBody
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
    case _: EndpointInput.FixedMethod[_] => true
    case _                               => false
  }

  object Responses {
    val NotFound: (StatusCode, String) = (StatusCode.NotFound, "Not Found")
    val MethodNotAllowed: (StatusCode, String) = (StatusCode.MethodNotAllowed, "Method Not Allowed")
  }
}
