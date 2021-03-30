package sttp.tapir.server.interceptor

import sttp.monad.MonadError
import sttp.tapir.model.{ServerRequest, ServerResponse}
import sttp.tapir.{DecodeResult, Endpoint, EndpointInput}

/** Intercepts requests, and endpoint decode events. Using interceptors it's possible to:
  *
  * - customise the request that is passed downstream
  * - short-circuit further processing and provide an alternate (or no) response
  * - replace or modify the response that is sent back to the client
  *
  * Interceptors can be called when the request is started to be processed (use [[RequestInterceptor]] in this case),
  * or for each endpoint, with either input success of failure decoding events (see [[EndpointInterceptor]]).
  *
  * To add an interceptors, modify the server options of the server interpreter.
  *
  * @tparam F The effect type constructor.
  * @tparam B The interpreter-specific, low-level type of body.
  */
sealed trait Interceptor[F[_], B]

/** Allows intercepting the handling of `request`, before decoding using any of the endpoints is done. The request
  * can be modified, before being passed to the `next` interceptor. Ultimately, `next` will call the logic of attempting
  * to decode subsequent endpoint inputs, using the given `request`.
  *
  * A request interceptor is called once for a request.
  *
  * Moreover, when calling `next`, an [[EndpointInterceptor]] can be provided, which will be added to the list of
  * endpoint interceptors to call. The order in which the endpoint interceptors will be called, will correspond to
  * their order in the interceptors list in the server options. An "empty" interceptor can be provided using
  * [[EndpointInterceptor.noop]].
  *
  * @tparam F The effect type constructor.
  * @tparam B The interpreter-specific, low-level type of body.
  */
trait RequestInterceptor[F[_], B] extends Interceptor[F, B] {
  def onRequest(
      request: ServerRequest,
      next: (ServerRequest, EndpointInterceptor[F, B]) => F[Option[ServerResponse[B]]]
  ): F[Option[ServerResponse[B]]]
}

/** Allows intercepting the handling of a request by an endpoint, when either the endpoint's inputs have been
  * decoded successfully, or when decoding has failed.
  * @tparam B The interpreter-specific, low-level type of body.
  */
trait EndpointInterceptor[F[_], B] extends Interceptor[F, B] {

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

object EndpointInterceptor {
  def noop[F[_], B]: EndpointInterceptor[F, B] = new EndpointInterceptor[F, B] {}
}
