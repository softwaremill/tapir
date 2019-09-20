package tapir.server

import tapir.DecodeResult.InvalidValue
import tapir.model.{StatusCode, StatusCodes}
import tapir._

object ServerDefaults {

  /**
    * @param badRequestOnPathFailureIfPathShapeMatches Should a status 400 be returned if the shape of the path
    * of the request matches, but decoding some path segment fails. This assumes that the only way decoding a path
    * segment might fail is with a DecodeResult.Error.
    */
  def decodeFailureHandlerUsingResponse(
      response: (StatusCode, String) => DecodeFailureHandling,
      badRequestOnPathFailureIfPathShapeMatches: Boolean,
      validationErrorToMessage: ValidationError[_] => String
  ): DecodeFailureHandler[Any] =
    (_, input, failure) => {
      val responseWithValidation = failure match {
        case InvalidValue(errors) if errors.nonEmpty =>
          val validationErrorMessage = errors.map(validationErrorToMessage).mkString(", ")
          (statusCode: StatusCode, msg: String) => response(statusCode, s"$msg ($validationErrorMessage)")
        case _ => response
      }

      input match {
        case EndpointInput.Query(name, _, _)       => responseWithValidation(StatusCodes.BadRequest, s"Invalid value for: query parameter $name")
        case _: EndpointInput.QueryParams          => responseWithValidation(StatusCodes.BadRequest, "Invalid value for: query parameters")
        case EndpointInput.Cookie(name, _, _)      => responseWithValidation(StatusCodes.BadRequest, s"Invalid value for: cookie $name")
        case EndpointIO.Header(name, _, _)         => responseWithValidation(StatusCodes.BadRequest, s"Invalid value for: header $name")
        case _: EndpointIO.Headers                 => responseWithValidation(StatusCodes.BadRequest, s"Invalid value for: headers")
        case _: EndpointIO.Body[_, _, _]           => responseWithValidation(StatusCodes.BadRequest, s"Invalid value for: body")
        case _: EndpointIO.StreamBodyWrapper[_, _] => responseWithValidation(StatusCodes.BadRequest, s"Invalid value for: body")
        // we assume that the only decode failure that might happen during path segment decoding is an error
        // a non-standard path decoder might return Missing/Multiple/Mismatch, but that would be indistinguishable from
        // a path shape mismatch
        case EndpointInput.PathCapture(_, name, _)
            if badRequestOnPathFailureIfPathShapeMatches && failure.isInstanceOf[DecodeResult.Error] =>
          responseWithValidation(StatusCodes.BadRequest, s"Invalid value for: path parameter ${name.getOrElse("?")}")
        case _ => DecodeFailureHandling.noMatch
      }
    }

  /**
    * By default, a 400 (bad request) is returned if a query, header or body input can't be decoded (for any reason),
    * or if decoding a path capture ends with an error.
    *
    * Otherwise (e.g. if the method, a path segment, or path capture is missing or there's a mismatch), a "no match" is
    * returned, which is a signal to try the next endpoint.
    */
  def decodeFailureHandler: DecodeFailureHandler[Any] =
    decodeFailureHandlerUsingResponse(failureResponse, badRequestOnPathFailureIfPathShapeMatches = false, validationErrorToMessage)

  def validationErrorToMessage(ve: ValidationError[_]): String = ve.validator match {
    case Validator.Min(value, exclusive) => s"expected ${ve.invalidValue} to be greater than ${if (exclusive) "" else "or equal to "}$value"
    case Validator.Max(value, exclusive) => s"expected ${ve.invalidValue} to be less than ${if (exclusive) "" else "or equal to "}$value"
    case Validator.Pattern(value)        => s"expected '${ve.invalidValue}' to match '$value'"
    case Validator.MinLength(value)      => s"expected size of ${ve.invalidValue} to be greater than or equal to $value "
    case Validator.MaxLength(value)      => s"expected size of ${ve.invalidValue} to be less than or equal to $value "
    case Validator.MinSize(value)        => s"expected collection (${ve.invalidValue}) to be greater than or equal to $value"
    case Validator.MaxSize(value)        => s"expected collection (${ve.invalidValue}) to be less than or equal to $value"
    case Validator.Custom(_, message)    => s"expected '${ve.invalidValue}' to pass custom validation: $message"
    case Validator.Enum(possibleValues)  => s"expected '${ve.invalidValue}' to be within $possibleValues"
  }

  val failureOutput: EndpointOutput[(StatusCode, String)] = statusCode.and(stringBody)
  def failureResponse(statusCode: StatusCode, message: String): DecodeFailureHandling =
    DecodeFailureHandling.response(failureOutput)((statusCode, message))

  val successStatusCode: StatusCode = StatusCodes.Ok
  val errorStatusCode: StatusCode = StatusCodes.BadRequest
}
