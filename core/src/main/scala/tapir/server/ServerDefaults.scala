package tapir.server

import tapir.model.{StatusCode, StatusCodes}
import tapir._

object ServerDefaults {

  /**
    * @param badRequestOnPathFailureIfPathShapeMatches Should a status 400 be returned if the shape of the path
    * of the request matches, but decoding some path segment fails. This assumes that the only way decoding a path
    * segment might fail is with a DecodeResult.Error.
    */
  def decodeFailureHandlerUsingResponse[REQUEST](
      response: (StatusCode, String) => DecodeFailureHandling,
      badRequestOnPathFailureIfPathShapeMatches: Boolean
  ): DecodeFailureHandler[REQUEST] =
    (_, input, failure) => {
      input match {
        case EndpointInput.Query(name, _, _)       => response(StatusCodes.BadRequest, s"Invalid value for: query parameter $name")
        case _: EndpointInput.QueryParams          => response(StatusCodes.BadRequest, "Invalid value for: query parameters")
        case EndpointInput.Cookie(name, _, _)      => response(StatusCodes.BadRequest, s"Invalid value for: cookie $name")
        case EndpointIO.Header(name, _, _)         => response(StatusCodes.BadRequest, s"Invalid value for: header $name")
        case _: EndpointIO.Headers                 => response(StatusCodes.BadRequest, s"Invalid value for: headers")
        case _: EndpointIO.Body[_, _, _]           => response(StatusCodes.BadRequest, s"Invalid value for: body")
        case _: EndpointIO.StreamBodyWrapper[_, _] => response(StatusCodes.BadRequest, s"Invalid value for: body")
        // we assume that the only decode failure that might happen during path segment decoding is an error
        // a non-standard path decoder might return Missing/Multiple/Mismatch, but that would be indisinguishable from
        // a path shape mismatch
        case EndpointInput.PathCapture(_, name, _)
            if badRequestOnPathFailureIfPathShapeMatches && failure.isInstanceOf[DecodeResult.Error] =>
          response(StatusCodes.BadRequest, s"Invalid value for: path parameter ${name.getOrElse("?")}")
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
  def decodeFailureHandler[REQUEST]: DecodeFailureHandler[REQUEST] =
    decodeFailureHandlerUsingResponse[REQUEST](failureResponse, badRequestOnPathFailureIfPathShapeMatches = true)

  val failureOutput: EndpointOutput[(StatusCode, String)] = statusCode.and(stringBody)
  def failureResponse(statusCode: StatusCode, message: String): DecodeFailureHandling =
    DecodeFailureHandling.response(failureOutput)((statusCode, message))

  val successStatusCode: StatusCode = StatusCodes.Ok
  val errorStatusCode: StatusCode = StatusCodes.BadRequest
}
