package sttp.tapir.client.sttp

import sttp.client.Request
import sttp.model.Uri
import sttp.tapir.{DecodeResult, Endpoint}

trait TapirSttpClient {
  implicit class RichEndpoint[I, E, O, S](e: Endpoint[I, E, O, S]) {

    /**
      * @throws IllegalArgumentException when response parsing fails
      */
    def toSttpRequestUnsafe(baseUri: Uri)(implicit clientOptions: SttpClientOptions): I => Request[Either[E, O], S] =
      new EndpointToSttpClient(clientOptions).toSttpRequestUnsafe(e, baseUri)

    def toSttpRequest(baseUri: Uri)(implicit clientOptions: SttpClientOptions): I => Request[DecodeResult[Either[E, O]], S] =
      new EndpointToSttpClient(clientOptions).toSttpRequest(e, baseUri)
  }
}
