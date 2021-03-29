package sttp.tapir.server.interceptor

import sttp.monad.MonadError
import sttp.tapir.model.{ServerRequest, ServerResponse}
import sttp.tapir.{DecodeResult, Endpoint, EndpointInput}

/** Allows intercepting the handling of a request by an endpoint, when either the endpoint's inputs have been
  * decoded successfully, or when decoding has failed.
  * @tparam B The interpreter-specific, low-level type of body.
  */
trait EndpointInterceptor[F[_], B] {

  /** Called when the the given `request` has been successfully decoded into inputs `i`, as described by
    * `endpoint.input`.
    *
    * `next(None)` will continue processing, ultimately (after the last interceptor) calling the endpoint's server
    * logic, and obtaining a response.
    *
    * `next(some output)` - providing an alternative value+output pair - will short-circuit further processing. The
    * given output will be translated to a response.
    *
    * @tparam I The type of the endpoint's inputs.
    * @return An effect, describing the server's response.
    */
  def onDecodeSuccess[I](
      request: ServerRequest,
      endpoint: Endpoint[I, _, _, _],
      i: I,
      next: Option[ValuedEndpointOutput[_]] => F[ServerResponse[B]]
  )(implicit monad: MonadError[F]): F[ServerResponse[B]] = next(None)

  /** Called when the the given `request` hasn't been successfully decoded into inputs `i`, as described by `endpoint`,
    * with `failure` occurring when decoding `failingInput`.
    *
    * `next(None)` will continue processing, ultimately (after the last interceptor) returning `None`, and attempting
    * to decode the next endpoint.
    *
    * `next(some output)` - providing an alternative value+output pair - will short-circuit further processing. The
    * given output will be translated to a response.
    *
    * @return An effect, describing the optional server response. If `None`, the next endpoint will be tried (if any).
    */
  def onDecodeFailure(
      request: ServerRequest,
      endpoint: Endpoint[_, _, _, _],
      failure: DecodeResult.Failure,
      failingInput: EndpointInput[_],
      next: Option[ValuedEndpointOutput[_]] => F[Option[ServerResponse[B]]]
  )(implicit monad: MonadError[F]): F[Option[ServerResponse[B]]] = next(None)
}
