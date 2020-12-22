package sttp.tapir.client.play

import play.api.libs.ws.{StandaloneWSClient, StandaloneWSRequest, StandaloneWSResponse}
import sttp.tapir.{DecodeResult, Endpoint}

@deprecated("Use PlayClientInterpreter", since = "0.17.1")
trait TapirPlayClient {

  implicit class RichPlayClientEndpoint[I, E, O, R](e: Endpoint[I, E, O, R]) {

    /** Interprets the endpoint as a client call, using the given `baseUri` as the starting point to create the target
      * uri.
      *
      * Returns:
      * - a function which, when applied to the endpoint's input parameters (given as a tuple), will encode them
      * to appropriate request parameters: path, query, headers and body. The result is a `StandaloneWSRequest`,
      * which can be sent using the `execute()` method.
      * - a response parser to use on the `StandaloneWSResponse` obtained after executing the request.
      */
    @deprecated("Use PlayClientInterpreter.toRequest", since = "0.17.1")
    def toPlayRequest(baseUri: String)(implicit
        clientOptions: PlayClientOptions,
        ws: StandaloneWSClient
    ): I => (StandaloneWSRequest, StandaloneWSResponse => DecodeResult[Either[E, O]]) =
      new EndpointToPlayClient(clientOptions, ws).toPlayRequest(e, baseUri)

    /** Interprets the endpoint as a client call, using the given `baseUri` as the starting point to create the target
      * uri.
      *
      * Returns:
      * - a function which, when applied to the endpoint's input parameters (given as a tuple), will encode them
      * to appropriate request parameters: path, query, headers and body. The result is a `StandaloneWSRequest`,
      * which can be sent using the `execute()` method.
      * - a response parser to use on the `StandaloneWSResponse` obtained after executing the request.
      *
      * @throws IllegalArgumentException when response parsing fails
      */
    @deprecated("Use PlayClientInterpreter.toRequestUnsafe", since = "0.17.1")
    def toPlayRequestUnsafe(
        baseUri: String
    )(implicit clientOptions: PlayClientOptions, ws: StandaloneWSClient): I => (StandaloneWSRequest, StandaloneWSResponse => Either[E, O]) =
      new EndpointToPlayClient(clientOptions, ws).toPlayRequestUnsafe(e, baseUri)

  }

}
