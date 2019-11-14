package sttp.tapir.server

import sttp.model.StatusCode
import sttp.tapir.DecodeResult.InvalidValue
import sttp.tapir._

object ServerDefaults {
  /**
    * @param badRequestOnPathErrorIfPathShapeMatches Should a status 400 be returned if the shape of the path
    * of the request matches, but decoding some path segment fails with a [[DecodeResult.Error]].
    * @param badRequestOnPathInvalidIfPathShapeMatches Should a status 400 be returned if the shape of the path
    * of the request matches, but decoding some path segment fails with a [[DecodeResult.InvalidValue]].
    */
  def decodeFailureHandlerUsingResponse(
      response: (StatusCode, String) => DecodeFailureHandling,
      badRequestOnPathErrorIfPathShapeMatches: Boolean,
      badRequestOnPathInvalidIfPathShapeMatches: Boolean,
      validationErrorsToMessage: List[ValidationError[_]] => String
  ): DecodeFailureHandler[Any] =
    (_, input, failure) => {
      val responseWithValidation = failure match {
        case InvalidValue(errors) if errors.nonEmpty =>
          val validationErrorsMessage = validationErrorsToMessage(errors)
          (statusCode: StatusCode, msg: String) => response(statusCode, s"$msg ($validationErrorsMessage)")
        case _ => response
      }

      input match {
        case EndpointInput.Query(name, _, _)       => responseWithValidation(StatusCode.BadRequest, s"Invalid value for: query parameter $name")
        case _: EndpointInput.QueryParams          => responseWithValidation(StatusCode.BadRequest, "Invalid value for: query parameters")
        case EndpointInput.Cookie(name, _, _)      => responseWithValidation(StatusCode.BadRequest, s"Invalid value for: cookie $name")
        case EndpointIO.Header(name, _, _)         => responseWithValidation(StatusCode.BadRequest, s"Invalid value for: header $name")
        case _: EndpointIO.Headers                 => responseWithValidation(StatusCode.BadRequest, s"Invalid value for: headers")
        case _: EndpointIO.Body[_, _, _]           => responseWithValidation(StatusCode.BadRequest, s"Invalid value for: body")
        case _: EndpointIO.StreamBodyWrapper[_, _] => responseWithValidation(StatusCode.BadRequest, s"Invalid value for: body")
        // we assume that the only decode failure that might happen during path segment decoding is an error
        // a non-standard path decoder might return Missing/Multiple/Mismatch, but that would be indistinguishable from
        // a path shape mismatch
        case EndpointInput.PathCapture(_, name, _)
            if (badRequestOnPathErrorIfPathShapeMatches && failure.isInstanceOf[DecodeResult.Error]) ||
              (badRequestOnPathInvalidIfPathShapeMatches && failure.isInstanceOf[DecodeResult.InvalidValue]) =>
          responseWithValidation(StatusCode.BadRequest, s"Invalid value for: path parameter ${name.getOrElse("?")}")
        case _ => DecodeFailureHandling.noMatch
      }
    }

  /**
    * By default, a 400 (bad request) is returned if a query, header or body input can't be decoded (for any reason),
    * or if decoding a path capture causes a validation error.
    *
    * Otherwise (e.g. if the method, a path segment, or path capture is missing, there's a mismatch or a decode error),
    * a "no match" is returned, which is a signal to try the next endpoint.
    */
  def decodeFailureHandler: DecodeFailureHandler[Any] =
    decodeFailureHandlerUsingResponse(
      failureResponse,
      badRequestOnPathErrorIfPathShapeMatches = false,
      badRequestOnPathInvalidIfPathShapeMatches = true,
      ValidationMessages.errorMessage
    )

  object ValidationMessages {
    /**
      * Default message describing why a value is invalid
      * @param valueName Name of the validated value to be used in error messages
      */
    def invalidValueMessage[T](ve: ValidationError[T], valueName: String): String = ve.validator match {
      case Validator.Min(value, exclusive) =>
        s"expected $valueName to be greater than ${if (exclusive) "" else "or equal to "}$value, but was ${ve.invalidValue}"
      case Validator.Max(value, exclusive) =>
        s"expected $valueName to be less than ${if (exclusive) "" else "or equal to "}$value, but was ${ve.invalidValue}"
      case Validator.Pattern(value)          => s"expected $valueName to match '$value', but was '${ve.invalidValue}'"
      case Validator.MinLength(value)        => s"expected $valueName to have length greater than or equal to $value, but was ${ve.invalidValue}"
      case Validator.MaxLength(value)        => s"expected $valueName to have length less than or equal to $value, but was ${ve.invalidValue} "
      case Validator.MinSize(value)          => s"expected size of $valueName to be greater than or equal to $value, but was ${ve.invalidValue.size}"
      case Validator.MaxSize(value)          => s"expected size of $valueName to be less than or equal to $value, but was ${ve.invalidValue.size}"
      case Validator.Custom(_, message)      => s"expected $valueName to pass custom validation: $message, but was '${ve.invalidValue}'"
      case Validator.Enum(possibleValues, _) => s"expected $valueName to be within $possibleValues, but was '${ve.invalidValue}'"
    }

    /**
      * Default message describing the path to an invalid value
      */
    def pathMessage(ve: ValidationError[_]): Option[String] = ve.path match {
      case Nil => None
      case l   => Some(l.map(_.lowLevelName).mkString("."))
    }

    /**
      * Default message describing the validation error: which value is invalid, and why
      */
    def errorMessage(ve: ValidationError[_]): String = invalidValueMessage(ve, pathMessage(ve).getOrElse("value"))

    /**
      * Default message describing a list of validation errors: which values are invalid, and why
      */
    def errorMessage(ve: List[ValidationError[_]]): String = ve.map(errorMessage).mkString(", ")
  }

  val failureOutput: EndpointOutput[(StatusCode, String)] = statusCode.and(stringBody)
  def failureResponse(statusCode: StatusCode, message: String): DecodeFailureHandling =
    DecodeFailureHandling.response(failureOutput)((statusCode, message))

  val successStatusCode: StatusCode = StatusCode.Ok
  val errorStatusCode: StatusCode = StatusCode.BadRequest
}
