package sttp.tapir.server

import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.{Endpoint, EndpointInfo, EndpointInfoOps, EndpointInput, EndpointMetaOps, EndpointOutput}

/** An [[Endpoint]] together with functions implementing the endpoint's security and main logic.
  *
  * @tparam R
  *   Requirements: The capabilities that are required by this endpoint's inputs/outputs. `Any`, if no requirements.
  * @tparam F
  *   The effect type used in the provided server logic.
  */
abstract class ServerEndpoint[-R, F[_]] extends EndpointInfoOps[R] with EndpointMetaOps {

  /** "Auth": Security input parameter types. */
  type A

  /** "User": The type of the value returned by the security logic. */
  type U

  /** Input parameter types. */
  type I

  /** Error output parameter types. */
  type E

  /** Output parameter types. */
  type O

  def endpoint: Endpoint[A, I, E, O, R]
  def securityLogic: MonadError[F] => A => F[Either[E, U]]
  def logic: MonadError[F] => U => I => F[Either[E, O]]

  override type ThisType[-_R] = ServerEndpoint.Full[A, U, I, E, O, _R, F]
  override def securityInput: EndpointInput[A] = endpoint.securityInput
  override def input: EndpointInput[I] = endpoint.input
  override def errorOutput: EndpointOutput[E] = endpoint.errorOutput
  override def output: EndpointOutput[O] = endpoint.output
  override def info: EndpointInfo = endpoint.info
  override private[tapir] def withInfo(info: EndpointInfo): ServerEndpoint.Full[A, U, I, E, O, R, F] =
    ServerEndpoint(endpoint.info(info), securityLogic, logic)

  override protected def showType: String = "ServerEndpoint"
}

object ServerEndpoint {
  private def emptySecurityLogic[E, F[_]]: MonadError[F] => Unit => F[Either[E, Unit]] = implicit m =>
    _ => (Right(()): Either[E, Unit]).unit

  /** The full type of a server endpoint, capturing the types of all input/output parameters. Most of the time, the simpler
    * `ServerEndpoint[R, F]` can be used instead.
    */
  type Full[_A, _U, _I, _E, _O, -R, F[_]] = ServerEndpoint[R, F] {
    type A = _A
    type U = _U
    type I = _I
    type E = _E
    type O = _O
  }

  /** Create a public server endpoint, with an empty (no-op) security logic, which always succeeds. */
  def public[I, E, O, R, F[_]](
      endpoint: Endpoint[Unit, I, E, O, R],
      logic: MonadError[F] => I => F[Either[E, O]]
  ): ServerEndpoint.Full[Unit, Unit, I, E, O, R, F] =
    ServerEndpoint(endpoint, emptySecurityLogic, m => _ => logic(m))

  /** Create a server endpoint, with the given security and main logic functions, which match the shape defined by `endpoint`. */
  def apply[A, U, I, E, O, R, F[_]](
      endpoint: Endpoint[A, I, E, O, R],
      securityLogic: MonadError[F] => A => F[Either[E, U]],
      logic: MonadError[F] => U => I => F[Either[E, O]]
  ): ServerEndpoint.Full[A, U, I, E, O, R, F] = {
    type _A = A
    type _U = U
    type _I = I
    type _E = E
    type _O = O
    val e = endpoint
    val s = securityLogic
    val l = logic
    new ServerEndpoint[R, F] {
      override type A = _A
      override type U = _U
      override type I = _I
      override type E = _E
      override type O = _O
      override def endpoint: Endpoint[A, I, E, O, R] = e
      override def securityLogic: MonadError[F] => A => F[Either[E, U]] = s
      override def logic: MonadError[F] => U => I => F[Either[E, O]] = l
    }
  }
}
