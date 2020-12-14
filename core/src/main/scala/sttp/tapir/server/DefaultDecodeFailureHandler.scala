package sttp.tapir.server

import sttp.model.{Header, StatusCode}
import sttp.tapir.Endpoint

final case class DefaultDecodeFailureResponse(status: StatusCode, headers: List[Header])

object DefaultDecodeFailureResponse {
  def status(status: StatusCode) = DefaultDecodeFailureResponse(status, Nil)
}

/** Create a decode failure handler, which:
  * - decides whether the given decode failure should lead to a response (and if so, with which status code and headers),
  *   or return a [[DecodeFailureHandling.noMatch]], using `respond`
  * - creates decode failure messages using `failureMessage`
  * - creates the response using `response`
  */
case class DefaultDecodeFailureHandler(
    respond: (DecodeFailureContext, Endpoint[_, _, _, _]) => Option[DefaultDecodeFailureResponse],
    response: (DefaultDecodeFailureResponse, String) => DecodeFailureHandling,
    failureMessage: DecodeFailureContext => String
) extends DecodeFailureHandler {
  def apply(ctx: DecodeFailureContext, endpoint: Endpoint[_, _, _, _]): DecodeFailureHandling = {
    respond(ctx, endpoint) match {
      case Some(c) =>
        val failureMsg = failureMessage(ctx)
        response(c, failureMsg)
      case None => DecodeFailureHandling.noMatch
    }
  }
}
