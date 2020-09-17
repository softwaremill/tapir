package sttp.tapir.client.sttp

import sttp.client3.Request
import sttp.model.Uri
import sttp.tapir.{DecodeResult, Endpoint}

trait TapirSttpClient {
  implicit class RichEndpoint[I, E, O, R](e: Endpoint[I, E, O, R]) {

    /**
      * Interprets the endpoint as a client call, using the given `baseUri` as the starting point to create the target
      * uri.
      *
      * Returns a function which, when applied to the endpoint's input parameters (given as a tuple), will encode them
      * to appropriate request parameters: path, query, headers and body. The result is a description of a request,
      * which can be sent using any sttp backend. The response will then contain the decoded error or success values
      * (note that this can be the body enriched with data from headers/status code).
      *
      * @throws IllegalArgumentException when response parsing fails
      */
    def toSttpRequestUnsafe(baseUri: Uri)(implicit clientOptions: SttpClientOptions): I => Request[Either[E, O], R] =
      new EndpointToSttpClient(clientOptions).toSttpRequestUnsafe(e, baseUri)

    /**
      * Interprets the endpoint as a client call, using the given `baseUri` as the starting point to create the target
      * uri.
      *
      * Returns a function which, when applied to the endpoint's input parameters (given as a tuple), will encode them
      * to appropriate request parameters: path, query, headers and body. The result is a description of a request,
      * which can be sent using any sttp backend. The response will then contain the decoded error or success values
      * (note that this can be the body enriched with data from headers/status code).
      */
    def toSttpRequest(baseUri: Uri)(implicit clientOptions: SttpClientOptions): I => Request[DecodeResult[Either[E, O]], R] =
      new EndpointToSttpClient(clientOptions).toSttpRequest(e, baseUri)
  }
}
