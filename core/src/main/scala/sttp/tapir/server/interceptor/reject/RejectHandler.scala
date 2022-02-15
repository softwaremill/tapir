package sttp.tapir.server.interceptor.reject

import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.server.interceptor.reject.RejectHandler._
import sttp.tapir.server.interceptor.{RequestResult, ValuedEndpointOutput}

trait RejectHandler {
  def apply(failure: RequestResult.Failure): Option[ValuedEndpointOutput[_]]
}

object RejectHandler {

  private[reject] def hasMethodMismatch(f: RequestResult.Failure): Boolean = f.failures.map(_.failingInput).exists {
    case _: EndpointInput.FixedMethod[_] => true
    case _                               => false
  }

  private[reject] object StatusCodeAndBody {
    val NotFound: (StatusCode, String) = (StatusCode.NotFound, "Not Found")
    val MethodNotAllowed: (StatusCode, String) = (StatusCode.MethodNotAllowed, "Method Not Allowed")
  }
}

case class DefaultRejectHandler(response: (StatusCode, String) => ValuedEndpointOutput[_]) extends RejectHandler {
  override def apply(failure: RequestResult.Failure): Option[ValuedEndpointOutput[_]] = {
    val statusCodeAndBody = if (hasMethodMismatch(failure)) Some(StatusCodeAndBody.MethodNotAllowed) else None
    statusCodeAndBody.map(response.tupled)
  }
}

object DefaultRejectHandler {
  val handler: DefaultRejectHandler = DefaultRejectHandler((sc, m) => ValuedEndpointOutput(statusCode.and(stringBody), (sc, m)))
}

case class DefaultOrNotFoundRejectHandler(response: (StatusCode, String) => ValuedEndpointOutput[_]) extends RejectHandler {
  override def apply(failure: RequestResult.Failure): Option[ValuedEndpointOutput[_]] = {
    val statusCodeAndBody = if (hasMethodMismatch(failure)) StatusCodeAndBody.MethodNotAllowed else StatusCodeAndBody.NotFound
    Some(response.tupled(statusCodeAndBody))
  }
}

object DefaultOrNotFoundRejectHandler {
  val handler: DefaultOrNotFoundRejectHandler =
    DefaultOrNotFoundRejectHandler((sc, m) => ValuedEndpointOutput(statusCode.and(stringBody), (sc, m)))
}
