package sttp.tapir.server.interceptor

import sttp.monad.MonadError
import sttp.tapir.server.interpreter.BodyListener
import sttp.tapir.server.model.ServerResponse

/** Handles the result of decoding a request using an endpoint's inputs. */
trait EndpointHandler[F[_], B] {

  /** Called when the request has been successfully decoded into data, and when the security logic succeeded. This is captured by the `ctx`
    * parameter.
    *
    * Called at most once per request.
    *
    * @tparam A
    *   The type of the endpoint's security inputs.
    * @tparam U
    *   Type of the successful result of the security logic.
    * @tparam I
    *   The type of the endpoint's inputs.
    * @return
    *   An effect, describing the server's response.
    */
  def onDecodeSuccess[A, U, I](
      ctx: DecodeSuccessContext[F, A, U, I]
  )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[ServerResponse[B]]

  /** Called when the security inputs have been successfully decoded into data, but the security logic failed (either with an error result
    * or an exception). This is captured by the `ctx` parameter.
    *
    * Called at most once per request.
    *
    * @tparam A
    *   The type of the endpoint's security inputs.
    * @return
    *   An effect, describing the server's response.
    */
  def onSecurityFailure[A](
      ctx: SecurityFailureContext[F, A]
  )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[ServerResponse[B]]

  /** Called when the given request hasn't been successfully decoded, because of the given failure on the given input. This is captured by
    * the `ctx` parameter.
    *
    * Might be called multiple times per request.
    *
    * @return
    *   An effect, describing the optional server response. If `None`, the next endpoint will be tried (if any).
    */
  def onDecodeFailure(
      ctx: DecodeFailureContext
  )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[Option[ServerResponse[B]]]
}
