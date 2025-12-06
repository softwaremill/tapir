package sttp.tapir.server.interceptor

import sttp.tapir.server.model.ServerResponse

/** The result of processing a request: either a response, or a list of endpoint decoding failures. */
sealed trait RequestResult[+B]
object RequestResult {

  /** @param source
    *   If the response was created by an [[EndpointHandler]], or a [[RequestHandler]]. This might impact the actions taken by interceptors,
    *   to ensure that certain actions are only taken exactly once for each response.
    */
  case class Response[B](response: ServerResponse[B], source: ResponseSource) extends RequestResult[B]
  case class Failure(failures: List[DecodeFailureContext]) extends RequestResult[Nothing]
}

sealed trait ResponseSource
object ResponseSource {
  case object EndpointHandler extends ResponseSource
  case object RequestHandler extends ResponseSource
}
