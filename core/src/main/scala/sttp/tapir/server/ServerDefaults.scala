package sttp.tapir.server

import sttp.model.StatusCode
import sttp.tapir._

import scala.annotation.tailrec

object ServerDefaults {
  /**
    * By default, a 400 (bad request) is returned if a query, header or body input can't be decoded (for any reason),
    * or if decoding a path capture causes a validation error.
    *
    * Otherwise (e.g. if the method, a path segment, or path capture is missing, there's a mismatch or a decode error),
    * a "no match" is returned, which is a signal to try the next endpoint.
    *
    * The default error messages contain information about the source of the decode error, and optionally the
    * validation error detail that caused the failure.
    */
  def decodeFailureHandler: DecodeFailureHandler[Any] =
    DecodeFailureHandler(
      FailureHandling.failureResponse,
      FailureHandling
        .respondWithStatusCode(_, badRequestOnPathErrorIfPathShapeMatches = false, badRequestOnPathInvalidIfPathShapeMatches = true),
      FailureMessages.failureMessage,
      ValidationMessages.validationErrorsMessage
    )

  object FailureHandling {
    val failureOutput: EndpointOutput[(StatusCode, String)] = statusCode.and(stringBody)

    def failureResponse(statusCode: StatusCode, message: String): DecodeFailureHandling =
      DecodeFailureHandling.response(failureOutput)((statusCode, message))

    /**
      * @param badRequestOnPathErrorIfPathShapeMatches Should a status 400 be returned if the shape of the path
      * of the request matches, but decoding some path segment fails with a [[DecodeResult.Error]].
      * @param badRequestOnPathInvalidIfPathShapeMatches Should a status 400 be returned if the shape of the path
      * of the request matches, but decoding some path segment fails with a [[DecodeResult.InvalidValue]].
      */
    def respondWithStatusCode(
        ctx: DecodeFailureContext[Any],
        badRequestOnPathErrorIfPathShapeMatches: Boolean,
        badRequestOnPathInvalidIfPathShapeMatches: Boolean
    ): Option[StatusCode] = {
      ctx.input match {
        case _: EndpointInput.Query[_]             => Some(StatusCode.BadRequest)
        case _: EndpointInput.QueryParams          => Some(StatusCode.BadRequest)
        case _: EndpointInput.Cookie[_]            => Some(StatusCode.BadRequest)
        case _: EndpointIO.Header[_]               => Some(StatusCode.BadRequest)
        case _: EndpointIO.Headers                 => Some(StatusCode.BadRequest)
        case _: EndpointIO.Body[_, _, _]           => Some(StatusCode.BadRequest)
        case _: EndpointIO.StreamBodyWrapper[_, _] => Some(StatusCode.BadRequest)
        // we assume that the only decode failure that might happen during path segment decoding is an error
        // a non-standard path decoder might return Missing/Multiple/Mismatch, but that would be indistinguishable from
        // a path shape mismatch
        case _: EndpointInput.PathCapture[_]
            if (badRequestOnPathErrorIfPathShapeMatches && ctx.failure.isInstanceOf[DecodeResult.Error]) ||
              (badRequestOnPathInvalidIfPathShapeMatches && ctx.failure.isInstanceOf[DecodeResult.InvalidValue]) =>
          Some(StatusCode.BadRequest)
        case _ => None
      }
    }
  }

  object FailureMessages {
    @tailrec
    def failureSourceMessage(input: EndpointInput.Single[_]): String = input match {
      case EndpointInput.FixedMethod(_)           => s"Invalid value for: method"
      case EndpointInput.FixedPath(_)             => s"Invalid value for: path segment"
      case EndpointInput.PathCapture(_, name, _)  => s"Invalid value for: path parameter ${name.getOrElse("?")}"
      case EndpointInput.PathsCapture(_)          => s"Invalid value for: path"
      case EndpointInput.Query(name, _, _)        => s"Invalid value for: query parameter $name"
      case _: EndpointInput.QueryParams           => "Invalid value for: query parameters"
      case EndpointInput.Cookie(name, _, _)       => s"Invalid value for: cookie $name"
      case _: EndpointInput.ExtractFromRequest[_] => "Invalid value"
      case a: EndpointInput.Auth[_]               => failureSourceMessage(a.input)
      case _: EndpointInput.Mapped[_, _]          => "Invalid value"
      case _: EndpointIO.Body[_, _, _]            => s"Invalid value for: body"
      case _: EndpointIO.StreamBodyWrapper[_, _]  => s"Invalid value for: body"
      case EndpointIO.Header(name, _, _)          => s"Invalid value for: header $name"
      case EndpointIO.FixedHeader(name, _, _)     => s"Invalid value for: header $name"
      case _: EndpointIO.Headers                  => s"Invalid value for: headers"
      case _: EndpointIO.Mapped[_, _]             => "Invalid value"
    }

    /**
      * Default message describing the source of a decode failure, alongside with optional error details.
      */
    def failureMessage(ctx: DecodeFailureContext[Any], detail: Option[String]): String = {
      val base = failureSourceMessage(ctx.input)
      detail match {
        case None    => base
        case Some(d) => s"$base ($d)"
      }
    }
  }

  /**
    * Messages describing decode failures that are due to validation.
    */
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
    def validationErrorMessage(ve: ValidationError[_]): String = invalidValueMessage(ve, pathMessage(ve).getOrElse("value"))

    /**
      * Default message describing a list of validation errors: which values are invalid, and why
      */
    def validationErrorsMessage(ve: List[ValidationError[_]]): String = ve.map(validationErrorMessage).mkString(", ")
  }

  object StatusCodes {
    val success: StatusCode = StatusCode.Ok
    val error: StatusCode = StatusCode.BadRequest
  }
}
