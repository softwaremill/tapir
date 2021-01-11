package sttp.tapir.client.sttp

import sttp.client3.{Request, SttpBackend}
import sttp.model.Uri
import sttp.monad.MonadError
import sttp.tapir.client.sttp.internal.Util
import sttp.tapir.{DecodeResult, Endpoint}

trait SttpClientInterpreter extends SttpClientInterpreterExtensions {

  def toClient[F[_], I, E, O](e: Endpoint[I, E, O, Any], baseUri: Uri, backend: SttpBackend[F, Any])(implicit
      clientOptions: SttpClientOptions,
      ev: MonadError[F]
  ): I => F[DecodeResult[Either[E, O]]] = {
    val req = new EndpointToSttpClient(clientOptions, WebSocketToPipe.webSocketsNotSupportedForAny).toSttpRequest(e, baseUri)
    (i: I) => ev.map(backend.send(req(i)))(_.body)
  }

  def toClientUnsafe[F[_], I, E, O](e: Endpoint[I, E, O, Any], baseUri: Uri, backend: SttpBackend[F, Any])(implicit
      clientOptions: SttpClientOptions,
      ev: MonadError[F]
  ): I => F[Either[E, O]] = {
    val req = new EndpointToSttpClient(clientOptions, WebSocketToPipe.webSocketsNotSupportedForAny).toSttpRequestUnsafe(e, baseUri)
    (i: I) => ev.map(backend.send(req(i)))(_.body)
  }

  def toClientThrowErrors[F[_], I, E, O](e: Endpoint[I, E, O, Any], baseUri: Uri, backend: SttpBackend[F, Any])(implicit
      clientOptions: SttpClientOptions,
      ev: MonadError[F]
  ): I => F[O] = {
    val req = new EndpointToSttpClient(clientOptions, WebSocketToPipe.webSocketsNotSupportedForAny).toSttpRequestUnsafe(e, baseUri)
    (i: I) =>
      ev.map(backend.send(req(i)))(res =>
        res.body match {
          case Left(err) => Util.throwError[I, E, O, Any](e, i, err)
          case Right(v)  => v
        }
      )
  }

  /** Interprets the endpoint as a client call, using the given `baseUri` as the starting point to create the target
    * uri.
    *
    * Returns a function which, when applied to the endpoint's input parameters (given as a tuple), will encode them
    * to appropriate request parameters: path, query, headers and body. The result is a description of a request,
    * which can be sent using any sttp backend. The response will then contain the decoded error or success values
    * (note that this can be the body enriched with data from headers/status code).
    *
    * @throws IllegalArgumentException when response parsing fails
    */
  def toRequestUnsafe[I, E, O, R](e: Endpoint[I, E, O, R], baseUri: Uri)(implicit
      clientOptions: SttpClientOptions,
      wsToPipe: WebSocketToPipe[R]
  ): I => Request[Either[E, O], R] =
    new EndpointToSttpClient(clientOptions, wsToPipe).toSttpRequestUnsafe(e, baseUri)

  /** Interprets the endpoint as a client call, using the given `baseUri` as the starting point to create the target
    * uri.
    *
    * Returns a function which, when applied to the endpoint's input parameters (given as a tuple), will encode them
    * to appropriate request parameters: path, query, headers and body. The result is a description of a request,
    * which can be sent using any sttp backend. The response will then contain the decoded error or success values
    * (note that this can be the body enriched with data from headers/status code).
    */
  def toRequest[I, E, O, R](e: Endpoint[I, E, O, R], baseUri: Uri)(implicit
      clientOptions: SttpClientOptions,
      wsToPipe: WebSocketToPipe[R]
  ): I => Request[DecodeResult[Either[E, O]], R] =
    new EndpointToSttpClient(clientOptions, wsToPipe).toSttpRequest(e, baseUri)
}

object SttpClientInterpreter extends SttpClientInterpreter
