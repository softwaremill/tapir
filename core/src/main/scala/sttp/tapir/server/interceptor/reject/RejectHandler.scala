package sttp.tapir.server.interceptor.reject

import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.server.ValuedEndpointOutput
import sttp.tapir.server.interceptor.reject.DefaultRejectHandler._
import sttp.tapir.server.interceptor.RequestResult

trait RejectHandler {
  def apply(failure: RequestResult.Failure): Option[ValuedEndpointOutput[_]]
}

case class DefaultRejectHandler(
    response: (StatusCode, String) => ValuedEndpointOutput[_],
    defaultStatusCodeAndBody: Option[(StatusCode, String)] = None
) extends RejectHandler {
  override def apply(failure: RequestResult.Failure): Option[ValuedEndpointOutput[_]] = {
    val statusCodeAndBody = if (hasMethodMismatch(failure)) Some(StatusCodeAndBody.MethodNotAllowed) else defaultStatusCodeAndBody
    statusCodeAndBody.map(response.tupled)
  }
}

object DefaultRejectHandler {
  val default: DefaultRejectHandler = DefaultRejectHandler((sc, m) => ValuedEndpointOutput(statusCode.and(stringBody), (sc, m)))

  val defaultOrNotFound: DefaultRejectHandler =
    DefaultRejectHandler((sc, m) => ValuedEndpointOutput(statusCode.and(stringBody), (sc, m)), Some(StatusCodeAndBody.NotFound))

  private def hasMethodMismatch(f: RequestResult.Failure): Boolean = f.failures.map(_.failingInput).exists {
    case _: EndpointInput.FixedMethod[_] => true
    case _                               => false
  }

  private object StatusCodeAndBody {
    val NotFound: (StatusCode, String) = (StatusCode.NotFound, "Not Found")
    val MethodNotAllowed: (StatusCode, String) = (StatusCode.MethodNotAllowed, "Method Not Allowed")
  }
}
