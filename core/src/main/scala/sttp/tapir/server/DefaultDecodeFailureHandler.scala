package sttp.tapir.server

import sttp.model.StatusCode
import sttp.tapir.DecodeResult.InvalidValue
import sttp.tapir.ValidationError

/**
  * Create a decode failure handler, which:
  * - decides, whether to respond with a failure message and the given status code, or return a
  *   [[DecodeFailureHandling.noMatch]], using `respondWithStatusCode`
  * - creates optional validation error messages using `validationErrorsToMessage`
  * - creates decode failure messages using `failureMessage` (passing in the optional validation
  *   error message)
  * - creates the response using `response`
  */
case class DefaultDecodeFailureHandler[R](
    respondWithStatusCode: DecodeFailureContext[R] => Option[StatusCode],
    response: (StatusCode, String) => DecodeFailureHandling,
    failureMessage: (DecodeFailureContext[R], Option[String]) => String,
    validationErrorsToMessage: List[ValidationError[_]] => String
) extends DecodeFailureHandler[R] {
  def apply(ctx: DecodeFailureContext[R]): DecodeFailureHandling = {
    respondWithStatusCode(ctx) match {
      case Some(c) =>
        val errorMsgDetail = ctx.failure match {
          case InvalidValue(errors) if errors.nonEmpty => Some(validationErrorsToMessage(errors))
          case _                                       => None
        }

        val failureMsg = failureMessage(ctx, errorMsgDetail)
        response(c, failureMsg)
      case None => DecodeFailureHandling.noMatch
    }
  }
}
