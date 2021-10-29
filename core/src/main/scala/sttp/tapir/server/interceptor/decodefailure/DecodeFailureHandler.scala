package sttp.tapir.server.interceptor.decodefailure

import sttp.model.{Header, HeaderNames, StatusCode}
import sttp.tapir.DecodeResult.Error.JsonDecodeException
import sttp.tapir.DecodeResult.{Error, InvalidValue}
import sttp.tapir.server.interceptor.{DecodeFailureContext, ValuedEndpointOutput}
import sttp.tapir.{DecodeResult, EndpointIO, EndpointInput, ValidationError, Validator, _}

import scala.annotation.tailrec

trait DecodeFailureHandler {

  /** Given the context in which a decode failure occurred (the request, the input and the failure), returns an optional response to the
    * request. `None` indicates that no action should be taken, and the request might be passed for decoding to other endpoints.
    *
    * Inputs are decoded in the following order: path, method, query, headers, body. Hence, if there's a decode failure on a query
    * parameter, any method & path inputs of the input must have matched and must have been decoded successfully.
    */
  def apply(ctx: DecodeFailureContext): Option[ValuedEndpointOutput[_]]
}

/** A decode failure handler, which:
  *   - decides whether the given decode failure should lead to a response (and if so, with which status code and headers), using `respond`
  *   - in case a response is sent, creates the message using `failureMessage`
  *   - in case a response is sent, creates the response using `response`, given the status code, headers, and the created failure message.
  *     By default, the headers might include authentication challenge.
  */
case class DefaultDecodeFailureHandler(
    respond: DecodeFailureContext => Option[(StatusCode, List[Header])],
    failureMessage: DecodeFailureContext => String,
    response: (StatusCode, List[Header], String) => ValuedEndpointOutput[_]
) extends DecodeFailureHandler {
  def apply(ctx: DecodeFailureContext): Option[ValuedEndpointOutput[_]] = {
    respond(ctx) match {
      case Some((sc, hs)) =>
        val failureMsg = failureMessage(ctx)
        Some(response(sc, hs, failureMsg))
      case None => None
    }
  }
}

object DefaultDecodeFailureHandler {

  /** The default implementation of the [[DecodeFailureHandler]].
    *
    * A 400 (bad request) is returned if a query, header or body input can't be decoded (for any reason), or if decoding a path capture
    * causes a validation error.
    *
    * Otherwise (e.g. if the method, a path segment, or path capture is missing, there's a mismatch or a decode error), `None` is returned,
    * which is a signal to try the next endpoint.
    *
    * The error messages contain information about the source of the decode error, and optionally the validation error detail that caused
    * the failure.
    *
    * This is only used for failures that occur when decoding inputs, not for exceptions that happen when the server logic is invoked.
    */
  def handler: DefaultDecodeFailureHandler = DefaultDecodeFailureHandler(
    respond(_, badRequestOnPathErrorIfPathShapeMatches = false, badRequestOnPathInvalidIfPathShapeMatches = true),
    FailureMessages.failureMessage,
    failureResponse
  )

  def failureResponse(c: StatusCode, hs: List[Header], m: String): ValuedEndpointOutput[_] =
    ValuedEndpointOutput(statusCode.and(headers).and(stringBody), (c, hs, m))

  /** @param badRequestOnPathErrorIfPathShapeMatches
    *   Should a status 400 be returned if the shape of the path of the request matches, but decoding some path segment fails with a
    *   [[DecodeResult.Error]].
    * @param badRequestOnPathInvalidIfPathShapeMatches
    *   Should a status 400 be returned if the shape of the path of the request matches, but decoding some path segment fails with a
    *   [[DecodeResult.InvalidValue]].
    */
  def respond(
      ctx: DecodeFailureContext,
      badRequestOnPathErrorIfPathShapeMatches: Boolean,
      badRequestOnPathInvalidIfPathShapeMatches: Boolean
  ): Option[(StatusCode, List[Header])] = {
    def onlyStatus(status: StatusCode): (StatusCode, List[Header]) = (status, Nil)

    failingInput(ctx) match {
      case _: EndpointInput.Query[_]       => Some(onlyStatus(StatusCode.BadRequest))
      case _: EndpointInput.QueryParams[_] => Some(onlyStatus(StatusCode.BadRequest))
      case _: EndpointInput.Cookie[_]      => Some(onlyStatus(StatusCode.BadRequest))
      case h: EndpointIO.Header[_] if ctx.failure.isInstanceOf[DecodeResult.Mismatch] && h.name == HeaderNames.ContentType =>
        Some(onlyStatus(StatusCode.UnsupportedMediaType))
      case _: EndpointIO.Header[_] => Some(onlyStatus(StatusCode.BadRequest))
      case fh: EndpointIO.FixedHeader[_] if ctx.failure.isInstanceOf[DecodeResult.Mismatch] && fh.h.name == HeaderNames.ContentType =>
        Some(onlyStatus(StatusCode.UnsupportedMediaType))
      case _: EndpointIO.FixedHeader[_]          => Some(onlyStatus(StatusCode.BadRequest))
      case _: EndpointIO.Headers[_]              => Some(onlyStatus(StatusCode.BadRequest))
      case _: EndpointIO.Body[_, _]              => Some(onlyStatus(StatusCode.BadRequest))
      case _: EndpointIO.StreamBodyWrapper[_, _] => Some(onlyStatus(StatusCode.BadRequest))
      // we assume that the only decode failure that might happen during path segment decoding is an error
      // a non-standard path decoder might return Missing/Multiple/Mismatch, but that would be indistinguishable from
      // a path shape mismatch
      case _: EndpointInput.PathCapture[_]
          if (badRequestOnPathErrorIfPathShapeMatches && ctx.failure.isInstanceOf[DecodeResult.Error]) ||
            (badRequestOnPathInvalidIfPathShapeMatches && ctx.failure.isInstanceOf[DecodeResult.InvalidValue]) =>
        Some(onlyStatus(StatusCode.BadRequest))
      case a: EndpointInput.Auth[_] => Some((StatusCode.Unauthorized, a.challenge.headers))
      // other basic endpoints - the request doesn't match, but not returning a response (trying other endpoints)
      case _: EndpointInput.Basic[_] => None
      // all other inputs (tuples, mapped) - responding with bad request
      case _ => Some(onlyStatus(StatusCode.BadRequest))
    }
  }

  private def failingInput(ctx: DecodeFailureContext) = {
    import sttp.tapir.internal.RichEndpointInput
    ctx.failure match {
      case DecodeResult.Missing =>
        def missingAuth(i: EndpointInput[_]) = i.pathTo(ctx.failingInput).collectFirst { case a: EndpointInput.Auth[_] =>
          a
        }
        missingAuth(ctx.endpoint.securityInput).orElse(missingAuth(ctx.endpoint.input)).getOrElse(ctx.failingInput)
      case _ => ctx.failingInput
    }
  }

  /** Default messages for [[DecodeResult.Failure]] s.
    */
  object FailureMessages {

    /** Describes the source of the failure: in which part of the request did the failure occur.
      */
    @tailrec
    def failureSourceMessage(input: EndpointInput[_]): String =
      input match {
        case EndpointInput.FixedMethod(_, _, _)      => s"Invalid value for: method"
        case EndpointInput.FixedPath(_, _, _)        => s"Invalid value for: path segment"
        case EndpointInput.PathCapture(name, _, _)   => s"Invalid value for: path parameter ${name.getOrElse("?")}"
        case EndpointInput.PathsCapture(_, _)        => s"Invalid value for: path"
        case EndpointInput.Query(name, _, _)         => s"Invalid value for: query parameter $name"
        case EndpointInput.QueryParams(_, _)         => "Invalid value for: query parameters"
        case EndpointInput.Cookie(name, _, _)        => s"Invalid value for: cookie $name"
        case _: EndpointInput.ExtractFromRequest[_]  => "Invalid value"
        case a: EndpointInput.Auth[_]                => failureSourceMessage(a.input)
        case _: EndpointInput.MappedPair[_, _, _, _] => "Invalid value"
        case _: EndpointIO.Body[_, _]                => s"Invalid value for: body"
        case _: EndpointIO.StreamBodyWrapper[_, _]   => s"Invalid value for: body"
        case EndpointIO.Header(name, _, _)           => s"Invalid value for: header $name"
        case EndpointIO.FixedHeader(name, _, _)      => s"Invalid value for: header $name"
        case EndpointIO.Headers(_, _)                => s"Invalid value for: headers"
        case _                                       => "Invalid value"
      }

    def combineSourceAndDetail(source: String, detail: Option[String]): String =
      detail match {
        case None    => source
        case Some(d) => s"$source ($d)"
      }

    /** Default message describing the source of a decode failure, alongside with optional validation details. */
    def failureMessage(ctx: DecodeFailureContext): String = {
      val base = failureSourceMessage(ctx.failingInput)

      val detail = ctx.failure match {
        case InvalidValue(errors) if errors.nonEmpty => Some(ValidationMessages.validationErrorsMessage(errors))
        case Error(_, error: JsonDecodeException)    => Some(error.getMessage)
        case _                                       => None
      }

      combineSourceAndDetail(base, detail)
    }
  }

  /** Default messages when the decode failure is due to a validation error. */
  object ValidationMessages {

    /** Default message describing why a value is invalid.
      * @param valueName
      *   Name of the validated value to be used in error messages
      */
    def invalidValueMessage[T](ve: ValidationError[T], valueName: String): String =
      ve match {
        case p: ValidationError.Primitive[T] =>
          p.validator match {
            case Validator.Min(value, exclusive) =>
              s"expected $valueName to be greater than ${if (exclusive) "" else "or equal to "}$value, but was ${ve.invalidValue}"
            case Validator.Max(value, exclusive) =>
              s"expected $valueName to be less than ${if (exclusive) "" else "or equal to "}$value, but was ${ve.invalidValue}"
            // TODO: convert to patterns when https://github.com/lampepfl/dotty/issues/12226 is fixed
            case p: Validator.Pattern[T] => s"expected $valueName to match '${p.value}', but was '${ve.invalidValue}'"
            case m: Validator.MinLength[T] =>
              s"expected $valueName to have length greater than or equal to ${m.value}, but was ${ve.invalidValue}"
            case m: Validator.MaxLength[T] =>
              s"expected $valueName to have length less than or equal to ${m.value}, but was ${ve.invalidValue} "
            case m: Validator.MinSize[T, Iterable] =>
              s"expected size of $valueName to be greater than or equal to ${m.value}, but was ${ve.invalidValue.size}"
            case m: Validator.MaxSize[T, Iterable] =>
              s"expected size of $valueName to be less than or equal to ${m.value}, but was ${ve.invalidValue.size}"
            case Validator.Enumeration(possibleValues, _, _) =>
              s"expected $valueName to be within $possibleValues, but was '${ve.invalidValue}'"
          }
        case c: ValidationError.Custom[T] =>
          s"expected $valueName to pass custom validation: ${c.message}, but was '${ve.invalidValue}'"
      }

    /** Default message describing the path to an invalid value. This is the path inside the validated object, e.g.
      * `user.address.street.name`.
      */
    def pathMessage(ve: ValidationError[_]): Option[String] =
      ve.path match {
        case Nil => None
        case l   => Some(l.map(_.encodedName).mkString("."))
      }

    /** Default message describing the validation error: which value is invalid, and why. */
    def validationErrorMessage(ve: ValidationError[_]): String = invalidValueMessage(ve, pathMessage(ve).getOrElse("value"))

    /** Default message describing a list of validation errors: which values are invalid, and why. */
    def validationErrorsMessage(ve: List[ValidationError[_]]): String = ve.map(validationErrorMessage).mkString(", ")
  }
}
