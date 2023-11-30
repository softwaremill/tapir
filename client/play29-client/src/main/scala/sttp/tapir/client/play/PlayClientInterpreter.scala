package sttp.tapir.client.play

import play.api.libs.ws.{StandaloneWSClient, StandaloneWSRequest, StandaloneWSResponse}
import sttp.tapir.{DecodeResult, Endpoint, PublicEndpoint}

trait PlayClientInterpreter {

  def playClientOptions: PlayClientOptions = PlayClientOptions.default

  // public

  /** Interprets the public endpoint as a client call, using the given `baseUri` as the starting point to create the target uri.
    *
    * Returns:
    *   - a function which, when applied to the endpoint's input parameters (given as a tuple), will encode them to appropriate request
    *     parameters: path, query, headers and body. The result is a `StandaloneWSRequest`, which can be sent using the `execute()` method.
    *   - a response parser to use on the `StandaloneWSResponse` obtained after executing the request.
    */
  def toRequest[I, E, O, R](e: PublicEndpoint[I, E, O, R], baseUri: String)(implicit
      ws: StandaloneWSClient
  ): I => (StandaloneWSRequest, StandaloneWSResponse => DecodeResult[Either[E, O]]) =
    new EndpointToPlayClient(playClientOptions, ws).toPlayRequest(e, baseUri).apply(())

  /** Interprets the public endpoint as a client call, using the given `baseUri` as the starting point to create the target uri.
    *
    * Returns:
    *   - a function which, when applied to the endpoint's input parameters (given as a tuple), will encode them to appropriate request
    *     parameters: path, query, headers and body. The result is a `StandaloneWSRequest`, which can be sent using the `execute()` method.
    *   - a response parser to use on the `StandaloneWSResponse` obtained after executing the request.
    *
    * @throws IllegalArgumentException
    *   when response parsing fails
    */
  def toRequestThrowDecodeFailures[I, E, O, R](e: PublicEndpoint[I, E, O, R], baseUri: String)(implicit
      ws: StandaloneWSClient
  ): I => (StandaloneWSRequest, StandaloneWSResponse => Either[E, O]) =
    new EndpointToPlayClient(playClientOptions, ws).toPlayRequestThrowDecodeFailures(e, baseUri).apply(())

  // secure

  /** Interprets the secure endpoint as a client call, using the given `baseUri` as the starting point to create the target uri.
    *
    * Returns:
    *   - a function which, when applied to the endpoint's security and regular input parameters (given as tuples), will encode them to
    *     appropriate request parameters: path, query, headers and body. The result is a `StandaloneWSRequest`, which can be sent using the
    *     `execute()` method.
    *   - a response parser to use on the `StandaloneWSResponse` obtained after executing the request.
    */
  def toSecureRequest[A, I, E, O, R](e: Endpoint[A, I, E, O, R], baseUri: String)(implicit
      ws: StandaloneWSClient
  ): A => I => (StandaloneWSRequest, StandaloneWSResponse => DecodeResult[Either[E, O]]) =
    new EndpointToPlayClient(playClientOptions, ws).toPlayRequest(e, baseUri)

  /** Interprets the secure endpoint as a client call, using the given `baseUri` as the starting point to create the target uri.
    *
    * Returns:
    *   - a function which, when applied to the endpoint's security and regular input parameters (given as tuples), will encode them to
    *     appropriate request parameters: path, query, headers and body. The result is a `StandaloneWSRequest`, which can be sent using the
    *     `execute()` method.
    *   - a response parser to use on the `StandaloneWSResponse` obtained after executing the request.
    *
    * @throws IllegalArgumentException
    *   when response parsing fails
    */
  def toSecureRequestThrowDecodeFailures[A, I, E, O, R](e: Endpoint[A, I, E, O, R], baseUri: String)(implicit
      ws: StandaloneWSClient
  ): A => I => (StandaloneWSRequest, StandaloneWSResponse => Either[E, O]) =
    new EndpointToPlayClient(playClientOptions, ws).toPlayRequestThrowDecodeFailures(e, baseUri)
}

object PlayClientInterpreter {
  def apply(clientOptions: PlayClientOptions = PlayClientOptions.default): PlayClientInterpreter = {
    new PlayClientInterpreter {
      override def playClientOptions: PlayClientOptions = clientOptions
    }
  }
}
