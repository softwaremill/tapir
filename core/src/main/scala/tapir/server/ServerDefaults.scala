package tapir.server

import tapir.model.StatusCodes
import tapir.{DecodeResult, EndpointIO, EndpointInput}

object ServerDefaults {

  /**
    * By default, a 400 (bad request) is returned if a query, header or body input can't be decoded (for any reason),
    * or if decoding a path capture ends with an error.
    *
    * Otherwise (e.g. if the method, a path segment, or path capture is missing or there's a mismatch), a "no match" is
    * returned, which is a signal to try the next endpoint.
    */
  def decodeFailureHandler[R]: DecodeFailureHandler[R] = (_, input, failure) => {
    input match {
      case EndpointInput.Query(name, _, _) =>
        DecodeFailureHandling.response(StatusCodes.BadRequest, s"Invalid value for: query parameter $name")
      case _: EndpointInput.QueryParams          => DecodeFailureHandling.response(StatusCodes.BadRequest, "Invalid value for: query parameters")
      case EndpointInput.Cookie(name, _, _)      => DecodeFailureHandling.response(StatusCodes.BadRequest, s"Invalid value for: cookie $name")
      case EndpointIO.Header(name, _, _)         => DecodeFailureHandling.response(StatusCodes.BadRequest, s"Invalid value for: header $name")
      case _: EndpointIO.Headers                 => DecodeFailureHandling.response(StatusCodes.BadRequest, s"Invalid value for: headers")
      case _: EndpointIO.Body[_, _, _]           => DecodeFailureHandling.response(StatusCodes.BadRequest, s"Invalid value for: body")
      case _: EndpointIO.StreamBodyWrapper[_, _] => DecodeFailureHandling.response(StatusCodes.BadRequest, s"Invalid value for: body")
      case _: EndpointInput.PathCapture[_] if failure.isInstanceOf[DecodeResult.Error] =>
        DecodeFailureHandling.response(StatusCodes.BadRequest, s"Invalid value for: path")
      case _: EndpointInput.PathsCapture if failure.isInstanceOf[DecodeResult.Error] =>
        DecodeFailureHandling.response(StatusCodes.BadRequest, s"Invalid value for: paths")
      case _ => DecodeFailureHandling.noMatch
    }
  }
}
