package sttp.tapir.client.sttp

import sttp.client3.HttpURLConnectionBackend
import sttp.model.Uri
import sttp.tapir.Endpoint
import sttp.tapir.client.sttp.internal.Util

trait SttpClientInterpreterExtensions {

  def toQuickClient[I, E, O](e: Endpoint[I, E, O, Any], baseUri: Uri)(implicit
      clientOptions: SttpClientOptions
  ): I => Either[E, O] = {
    val backend = HttpURLConnectionBackend()
    val req = new EndpointToSttpClient(clientOptions, WebSocketToPipe.webSocketsNotSupportedForAny).toSttpRequestUnsafe(e, baseUri)
    (i: I) => backend.send(req(i)).body
  }

  def toQuickClientThrowErrors[I, E, O](e: Endpoint[I, E, O, Any], baseUri: Uri)(implicit
      clientOptions: SttpClientOptions
  ): I => O = {
    val backend = HttpURLConnectionBackend()
    val req = new EndpointToSttpClient(clientOptions, WebSocketToPipe.webSocketsNotSupportedForAny).toSttpRequestUnsafe(e, baseUri)
    (i: I) =>
      backend
        .send(req(i))
        .body
        .fold(
          err => Util.throwError[I, E, O, Any](e, i, err),
          identity
        )
  }
}
