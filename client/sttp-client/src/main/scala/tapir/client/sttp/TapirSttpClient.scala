package tapir.client.sttp

import sttp.client.Request
import sttp.model.Uri
import tapir.Endpoint

trait TapirSttpClient {
  implicit class RichEndpoint[I, E, O, S](e: Endpoint[I, E, O, S]) {
    def toSttpRequest(baseUri: Uri)(implicit clientOptions: SttpClientOptions): I => Request[Either[E, O], S] =
      new EndpointToSttpClient(clientOptions).toSttpRequest(e, baseUri)
  }
}
