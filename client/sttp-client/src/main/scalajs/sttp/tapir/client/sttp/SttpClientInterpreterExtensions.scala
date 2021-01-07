package sttp.tapir.client.sttp

import sttp.client3.FetchBackend
import sttp.model.Uri
import sttp.tapir.Endpoint
import sttp.tapir.client.sttp.internal.Util

import scala.concurrent.Future

trait SttpClientInterpreterExtensions {
  def toQuickClient[I, E, O](e: Endpoint[I, E, O, Any], baseUri: Uri)(implicit
      clientOptions: SttpClientOptions
  ): I => Future[Either[E, O]] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    val backend = FetchBackend()
    val req = new EndpointToSttpClient(clientOptions, WebSocketToPipe.webSocketsNotSupportedForAny).toSttpRequestUnsafe(e, baseUri)
    (i: I) => backend.send(req(i)).map(_.body)
  }

  def toQuickClientThrowErrors[I, E, O](e: Endpoint[I, E, O, Any], baseUri: Uri)(implicit
      clientOptions: SttpClientOptions
  ): I => Future[O] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    val backend = FetchBackend()
    val req = new EndpointToSttpClient(clientOptions, WebSocketToPipe.webSocketsNotSupportedForAny).toSttpRequestUnsafe(e, baseUri)
    (i: I) =>
      backend
        .send(req(i))
        .map(resp =>
          resp.body
            .fold(
              err => Util.throwError(e, i, err),
              identity
            )
        )
  }
}

