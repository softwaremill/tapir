package sttp.tapir.server

import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.typelevel.ErasureSameAsType
import sttp.tapir._

import scala.reflect.ClassTag

/** An [[Endpoint]] together with functions implementing the endpoint's security and main logic.
  *
  * @tparam R
  *   Requirements: The capabilities that are required by this endpoint's inputs/outputs. `Any`, if no requirements.
  * @tparam F
  *   The effect type constructor used in the provided server logic.
  */
abstract class ServerEndpoint[-R, F[_]] extends EndpointInfoOps[R] with EndpointMetaOps { outer =>

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

  /** Prepends additional security logic to this endpoint. This is useful when adding security to file/resource-serving endpoints. The
    * additional security logic should return a `Right(())` to indicate success, or a `Left(E2)` to indicate failure; in this case, the
    * given error output will be used to create the response.
    *
    * The security inputs will become a tuple, containing first `additionalSecurityInput` combined with the current
    * `endpoint.securityInput`.
    *
    * The error output will consist of two variants: either the `securityErrorOutput` (the [[ClassTag]] requirement for `E2` is used to
    * create the [[oneOfVariant]]). In the absence of sum types, the resulting errors are types as `Any`.
    *
    * The security logic is modified so that first `additionalSecurityLogic` is run, followed by the security logic defined so far.
    *
    * The type of the value returned by the combined security logic, or the regular logic remains unchanged.
    *
    * @tparam A2
    *   Type of the additional security input.
    * @tparam E2
    *   Type of the error output for the security logic.
    */
  def prependSecurity[A2, E2: ClassTag: ErasureSameAsType](
      additionalSecurityInput: EndpointInput[A2],
      securityErrorOutput: EndpointOutput[E2]
  )(
      additionalSecurityLogic: A2 => F[Either[E2, Unit]]
  ): ServerEndpoint[R, F] = prependSecurity_(additionalSecurityInput, securityErrorOutput)(_ => additionalSecurityLogic)

  /** See [[prependSecurity]]. */
  def prependSecurityPure[A2, E2: ClassTag: ErasureSameAsType](
      additionalSecurityInput: EndpointInput[A2],
      securityErrorOutput: EndpointOutput[E2]
  )(
      additionalSecurityLogic: A2 => Either[E2, Unit]
  ): ServerEndpoint[R, F] = prependSecurity_(additionalSecurityInput, securityErrorOutput)(implicit m => additionalSecurityLogic(_).unit)

  private def prependSecurity_[A2, E2: ClassTag: ErasureSameAsType](
      additionalSecurityInput: EndpointInput[A2],
      securityErrorOutput: EndpointOutput[E2]
  )(
      additionalSecurityLogic: MonadError[F] => A2 => F[Either[E2, Unit]]
  ): ServerEndpoint[R, F] =
    new ServerEndpoint[R, F] {
      override type A = (A2, outer.A)
      override type U = outer.U
      override type I = outer.I
      override type E = Any
      override type O = outer.O

      override def endpoint: Endpoint[A, I, E, O, R] =
        outer.endpoint
          .prependSecurityIn(additionalSecurityInput)
          .errorOutVariantsFromCurrent[Any](current => List(oneOfVariant[E2](securityErrorOutput), oneOfDefaultVariant(current)))

      override def securityLogic: MonadError[F] => A => F[Either[E, U]] = implicit m =>
        a =>
          additionalSecurityLogic(m)(a._1).flatMap {
            case Left(e2)  => (Left(e2): Either[E, U]).unit
            case Right(()) => outer.securityLogic(m)(a._2).asInstanceOf[F[Either[E, U]]] // avoiding .map(identity)
          }

      // we're widening the `E` type, the logic still will always return `outer.E`, so this cast is safe
      // instead, we could have written: `implicit m => u => i => outer.logic(m)(u)(i).map(identity)`
      override def logic: MonadError[F] => U => I => F[Either[E, O]] = outer.logic.asInstanceOf[MonadError[F] => U => I => F[Either[E, O]]]
    }
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
