package tapir.server

import tapir.model.{StatusCode, StatusCodes}
import tapir._

object ServerDefaults {

  def decodeFailureHandlerUsingResponse[REQUEST](
      response: (StatusCode, String) => DecodeFailureHandling
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
        case _                                     => DecodeFailureHandling.noMatch
      }
    }

  /**
    * By default, a 400 (bad request) is returned if a query, header or body input can't be decoded (for any reason),
    * or if decoding a path capture ends with an error.
    *
    * Otherwise (e.g. if the method, a path segment, or path capture is missing or there's a mismatch), a "no match" is
    * returned, which is a signal to try the next endpoint.
    */
  def decodeFailureHandler[REQUEST]: DecodeFailureHandler[REQUEST] = decodeFailureHandlerUsingResponse[REQUEST](failureResponse)

  private val failureOutput: EndpointOutput[(StatusCode, String)] = statusCode.and(stringBody)
  private def failureResponse(statusCode: StatusCode, message: String): DecodeFailureHandling =
    DecodeFailureHandling.response(failureOutput)((statusCode, message))

  val errorStatusCode: StatusCode = StatusCodes.BadRequest
}
