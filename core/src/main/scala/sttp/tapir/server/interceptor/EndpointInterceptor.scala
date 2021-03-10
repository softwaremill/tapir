package sttp.tapir.server.interceptor

import sttp.monad.MonadError
import sttp.tapir.model.{ServerRequest, ServerResponse}
import sttp.tapir.{DecodeResult, Endpoint, EndpointInput}

trait EndpointInterceptor[F[_]] {

  /** Called when the the given `request` has been successfully decoded into inputs `i`, as described by
    * `endpoint.input`.
    *
    * Use `next(None)` to continue processing, ultimately (after the last interceptor) calling the endpoint's server
    * logic, and obtaining a response. Or, provide an alternative value+output pair, which will be used as the response.
    *
    * @tparam I The type of the endpoint's inputs.
    * @tparam WB The interpreter-specific, low-level type of body, returned in case of a web socket response.
    * @tparam B The interpreter-specific, low-level type of body, returned in case of a regular response.
    * @return An effect, describing the server's response.
    */
  def onDecodeSuccess[I, WB, B](
      request: ServerRequest,
      endpoint: Endpoint[I, _, _, _],
      i: I,
      next: Option[ValuedEndpointOutput[_]] => F[ServerResponse[WB, B]]
  )(implicit monad: MonadError[F]): F[ServerResponse[WB, B]] = next(None)

  /** Called when the the given `request` hasn't been successfully decoded into inputs `i`, as described by `endpoint`,
    * with `failure` occurring when decoding `failingInput`.
    *
    * Use `next(None)` to continue processing, ultimately (after the last interceptor) returning `None`, and attempting
    * to decode the next endpoint. Or, provide an alternative value+output pair, which will be used as the response.
    *
    * @tparam WB The interpreter-specific, low-level type of body, returned in case of a web socket response.
    * @tparam B The interpreter-specific, low-level type of body, returned in case of a regular response.
    * @return An effect, describing the optional server response. If `None`, the next endpoint will be tried (if any).
    */
  def onDecodeFailure[WB, B](
      request: ServerRequest,
      endpoint: Endpoint[_, _, _, _],
      failure: DecodeResult.Failure,
      failingInput: EndpointInput[_],
      next: Option[ValuedEndpointOutput[_]] => F[Option[ServerResponse[WB, B]]]
  )(implicit monad: MonadError[F]): F[Option[ServerResponse[WB, B]]] = next(None)
}
