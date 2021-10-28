package sttp.tapir.server

import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.{Endpoint, EndpointInfo, EndpointInfoOps, EndpointInput, EndpointMetaOps, EndpointOutput}

/** @tparam A
  *   Security input parameter types.
  * @tparam U
  *   Security input parameter types.
  * @tparam I
  *   Input parameter types.
  * @tparam E
  *   Error output parameter types.
  * @tparam O
  *   Output parameter types.
  * @tparam R
  *   The capabilities that are required by this endpoint's inputs/outputs. `Any`, if no requirements.
  * @tparam F
  *   The effect type used in the provided server logic.
  */
case class ServerEndpoint[A, U, I, E, O, -R, F[_]](
    endpoint: Endpoint[A, I, E, O, R],
    securityLogic: MonadError[F] => A => F[Either[E, U]],
    logic: MonadError[F] => U => I => F[Either[E, O]]
) extends EndpointInfoOps[A, I, E, O, R]
    with EndpointMetaOps[A, I, E, O, R] {

  override type EndpointType[_A, _I, _E, _O, -_R] = ServerEndpoint[_A, U, _I, _E, _O, _R, F]
  override def securityInput: EndpointInput[A] = endpoint.securityInput
  override def input: EndpointInput[I] = endpoint.input
  override def errorOutput: EndpointOutput[E] = endpoint.errorOutput
  override def output: EndpointOutput[O] = endpoint.output
  override def info: EndpointInfo = endpoint.info
  override private[tapir] def withInfo(info: EndpointInfo): ServerEndpoint[A, U, I, E, O, R, F] = copy(endpoint = endpoint.info(info))

  override protected def showType: String = "ServerEndpoint"
}

object ServerEndpoint {
  private def emptySecurityLogic[E, F[_]]: MonadError[F] => Unit => F[Either[E, Unit]] = implicit m =>
    _ => (Right(()): Either[E, Unit]).unit

  def public[I, E, O, R, F[_]](
      endpoint: Endpoint[Unit, I, E, O, R],
      logic: MonadError[F] => I => F[Either[E, O]]
  ): ServerEndpoint[Unit, Unit, I, E, O, R, F] =
    ServerEndpoint(endpoint, emptySecurityLogic, m => _ => logic(m))
}
