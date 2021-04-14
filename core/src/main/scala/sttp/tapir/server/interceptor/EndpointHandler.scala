package sttp.tapir.server.interceptor

import sttp.monad.MonadError
import sttp.tapir.model.ServerResponse

/** Handles the result of decoding a request using an endpoint's inputs. */
trait EndpointHandler[F[_], B] {

  /** Called when the request has been successfully decoded into data. This is captured by the `ctx` parameter.
    *
    * @tparam I The type of the endpoint's inputs.
    * @return An effect, describing the server's response.
    */
  def onDecodeSuccess[I](ctx: DecodeSuccessContext[F, I])(implicit monad: MonadError[F]): F[ServerResponse[B]]

  /** Called when the given request hasn't been successfully decoded, because of the given failure on the given
    * input. This is captured by the `ctx` parameter.
    *
    * @return An effect, describing the optional server response. If `None`, the next endpoint will be tried (if any).
    */
  def onDecodeFailure(ctx: DecodeFailureContext)(implicit monad: MonadError[F]): F[Option[ServerResponse[B]]]
}
