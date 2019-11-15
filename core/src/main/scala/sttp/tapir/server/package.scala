package sttp.tapir

import sttp.model.StatusCode
import sttp.tapir.DecodeResult.InvalidValue

package object server {
  /**
    * Given the request, the input for which value decoding failed, and the decode value, returns the action
    * that should be taken.
    */
  type DecodeFailureHandler[-REQUEST] = (REQUEST, EndpointInput.Single[_], DecodeFailure) => DecodeFailureHandling

  object DecodeFailureHandler {
    /**
      * Create a decode failure handler, which:
      * - creates optional validation error messages using `validationErrorsToMessage`
      * - creates decode failure messages using `validationErrorsToMessage` (passing in the optional validation
      *   error message)
      * - decides, whether to respond with the given status code, or return a [[DecodeFailureHandling.noMatch]], using
      *   `respondWithStatusCode`
      * - creates the response using `response`
      */
    def apply(
        response: (StatusCode, String) => DecodeFailureHandling,
        respondWithStatusCode: (EndpointInput.Single[_], DecodeFailure) => Option[StatusCode],
        failureMessage: (EndpointInput.Single[_], Option[String]) => String,
        validationErrorsToMessage: List[ValidationError[_]] => String
    ): DecodeFailureHandler[Any] =
      (_, input, failure) => {
        respondWithStatusCode(input, failure) match {
          case Some(c) =>
            val errorMsgDetail = failure match {
              case InvalidValue(errors) if errors.nonEmpty => Some(validationErrorsToMessage(errors))
              case _                                       => None
            }

            val failureMsg = failureMessage(input, errorMsgDetail)
            response(c, failureMsg)
          case None => DecodeFailureHandling.noMatch
        }
      }
  }
}
