package sttp.tapir.server.interceptor

import sttp.tapir.server.model.ServerResponse

/** The result of processing a request: either a response, or a list of endpoint decoding failures. */
sealed trait RequestResult[+B]
object RequestResult {
  case class Response[B](response: ServerResponse[B]) extends RequestResult[B]
  case class Failure(failures: List[DecodeFailureContext]) extends RequestResult[Nothing]
}
