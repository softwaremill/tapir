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

  /** Security input parameter types (abbreviated as `A`). */
  type SECURITY_INPUT

  /** The type of the value returned by the security logic, e.g. a user (abbreviated as `U`). */
  type PRINCIPAL

  /** Input parameter types (abbreviated as `I`). */
  type INPUT

  /** Error output parameter types (abbreviated as `E`). */
  type ERROR_OUTPUT

  /** Output parameter types (abbreviated as `O`). */
  type OUTPUT

  def endpoint: Endpoint[SECURITY_INPUT, INPUT, ERROR_OUTPUT, OUTPUT, R]
  def securityLogic: MonadError[F] => SECURITY_INPUT => F[Either[ERROR_OUTPUT, PRINCIPAL]]
  def logic: MonadError[F] => PRINCIPAL => INPUT => F[Either[ERROR_OUTPUT, OUTPUT]]

  override type ThisType[-_R] = ServerEndpoint.Full[SECURITY_INPUT, PRINCIPAL, INPUT, ERROR_OUTPUT, OUTPUT, _R, F]
  override def securityInput: EndpointInput[SECURITY_INPUT] = endpoint.securityInput
  override def input: EndpointInput[INPUT] = endpoint.input
  override def errorOutput: EndpointOutput[ERROR_OUTPUT] = endpoint.errorOutput
  override def output: EndpointOutput[OUTPUT] = endpoint.output
  override def info: EndpointInfo = endpoint.info
  override private[tapir] def withInfo(
      info: EndpointInfo
  ): ServerEndpoint.Full[SECURITY_INPUT, PRINCIPAL, INPUT, ERROR_OUTPUT, OUTPUT, R, F] =
    ServerEndpoint(endpoint.info(info), securityLogic, logic)

  override protected def showType: String = "ServerEndpoint"

  /** Prepends an additional input to this endpoint. This is useful when adding a context path to endpoints, e.g.
    * `serverEndpoint.prependIn("api" / "v1")`.
    *
    * The given endpoint can't map to any values in the request, hence its type is `EndpointInput[Unit]`.
    *
    * The input is prepended to the security input, so that it comes before any other path-related inputs (either security or regular).
    */
  def prependSecurityIn(additionalInput: EndpointInput[Unit]): ServerEndpoint[R, F] = new ServerEndpoint[R, F] {
    override type SECURITY_INPUT = outer.SECURITY_INPUT
    override type PRINCIPAL = outer.PRINCIPAL
    override type INPUT = outer.INPUT
    override type ERROR_OUTPUT = outer.ERROR_OUTPUT
    override type OUTPUT = outer.OUTPUT
    override def endpoint: Endpoint[SECURITY_INPUT, INPUT, ERROR_OUTPUT, OUTPUT, R] = outer.endpoint.prependSecurityIn(additionalInput)
    override def securityLogic: MonadError[F] => SECURITY_INPUT => F[Either[ERROR_OUTPUT, PRINCIPAL]] = outer.securityLogic
    override def logic: MonadError[F] => PRINCIPAL => INPUT => F[Either[ERROR_OUTPUT, OUTPUT]] = outer.logic
  }

  /** Prepends additional security logic to this endpoint. This is useful when adding security to file/resource-serving endpoints. The
    * additional security logic should return a `Right(())` to indicate success, or a `Left(E2)` to indicate failure; in this case, the
    * given error output will be used to create the response.
    *
    * The security inputs will become a tuple, containing first `additionalSecurityInput` combined with the current
    * `endpoint.securityInput`.
    *
    * The error output will consist of two variants: either the `securityErrorOutput` (the [[ClassTag]] requirement for `E2` is used to
    * create the [[oneOfVariant]]). In the absence of sum types, the resulting errors are typed as `Any`.
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
      override type SECURITY_INPUT = (A2, outer.SECURITY_INPUT)
      override type PRINCIPAL = outer.PRINCIPAL
      override type INPUT = outer.INPUT
      override type ERROR_OUTPUT = Any
      override type OUTPUT = outer.OUTPUT

      override def endpoint: Endpoint[SECURITY_INPUT, INPUT, ERROR_OUTPUT, OUTPUT, R] =
        outer.endpoint
          .prependSecurityIn(additionalSecurityInput)
          .errorOutVariantsFromCurrent[Any](current => List(oneOfVariant[E2](securityErrorOutput), oneOfDefaultVariant(current)))

      override def securityLogic: MonadError[F] => SECURITY_INPUT => F[Either[ERROR_OUTPUT, PRINCIPAL]] = implicit m =>
        a =>
          additionalSecurityLogic(m)(a._1).flatMap {
            case Left(e2)  => (Left(e2): Either[ERROR_OUTPUT, PRINCIPAL]).unit
            case Right(()) => outer.securityLogic(m)(a._2).asInstanceOf[F[Either[ERROR_OUTPUT, PRINCIPAL]]] // avoiding .map(identity)
          }

      // we're widening the `E` type, the logic still will always return `outer.E`, so this cast is safe
      // instead, we could have written: `implicit m => u => i => outer.logic(m)(u)(i).map(identity)`
      override def logic: MonadError[F] => PRINCIPAL => INPUT => F[Either[ERROR_OUTPUT, OUTPUT]] =
        outer.logic.asInstanceOf[MonadError[F] => PRINCIPAL => INPUT => F[Either[ERROR_OUTPUT, OUTPUT]]]
    }
}

object ServerEndpoint {
  private def emptySecurityLogic[E, F[_]]: MonadError[F] => Unit => F[Either[E, Unit]] = implicit m =>
    _ => (Right(()): Either[E, Unit]).unit

  /** The full type of a server endpoint, capturing the types of all input/output parameters. Most of the time, the simpler
    * `ServerEndpoint[R, F]` can be used instead.
    */
  type Full[_SECURITY_INPUT, _PRINCIPAL, _INPUT, _ERROR_OUTPUT, _OUTPUT, -R, F[_]] = ServerEndpoint[R, F] {
    type SECURITY_INPUT = _SECURITY_INPUT
    type PRINCIPAL = _PRINCIPAL
    type INPUT = _INPUT
    type ERROR_OUTPUT = _ERROR_OUTPUT
    type OUTPUT = _OUTPUT
  }

  /** Create a public server endpoint, with an empty (no-op) security logic, which always succeeds. */
  def public[INPUT, ERROR_OUTPUT, OUTPUT, R, F[_]](
      endpoint: Endpoint[Unit, INPUT, ERROR_OUTPUT, OUTPUT, R],
      logic: MonadError[F] => INPUT => F[Either[ERROR_OUTPUT, OUTPUT]]
  ): ServerEndpoint.Full[Unit, Unit, INPUT, ERROR_OUTPUT, OUTPUT, R, F] =
    ServerEndpoint(endpoint, emptySecurityLogic, m => _ => logic(m))

  /** Create a server endpoint, with the given security and main logic functions, which match the shape defined by `endpoint`. */
  def apply[SECURITY_INPUT, PRINCIPAL, INPUT, ERROR_OUTPUT, OUTPUT, R, F[_]](
      endpoint: Endpoint[SECURITY_INPUT, INPUT, ERROR_OUTPUT, OUTPUT, R],
      securityLogic: MonadError[F] => SECURITY_INPUT => F[Either[ERROR_OUTPUT, PRINCIPAL]],
      logic: MonadError[F] => PRINCIPAL => INPUT => F[Either[ERROR_OUTPUT, OUTPUT]]
  ): ServerEndpoint.Full[SECURITY_INPUT, PRINCIPAL, INPUT, ERROR_OUTPUT, OUTPUT, R, F] = {
    type _SECURITY_INPUT = SECURITY_INPUT
    type _PRINCIPAL = PRINCIPAL
    type _INPUT = INPUT
    type _ERROR_OUTPUT = ERROR_OUTPUT
    type _OUTPUT = OUTPUT
    val e = endpoint
    val s = securityLogic
    val l = logic
    new ServerEndpoint[R, F] {
      override type SECURITY_INPUT = _SECURITY_INPUT
      override type PRINCIPAL = _PRINCIPAL
      override type INPUT = _INPUT
      override type ERROR_OUTPUT = _ERROR_OUTPUT
      override type OUTPUT = _OUTPUT
      override def endpoint: Endpoint[SECURITY_INPUT, INPUT, ERROR_OUTPUT, OUTPUT, R] = e
      override def securityLogic: MonadError[F] => SECURITY_INPUT => F[Either[ERROR_OUTPUT, PRINCIPAL]] = s
      override def logic: MonadError[F] => PRINCIPAL => INPUT => F[Either[ERROR_OUTPUT, OUTPUT]] = l
    }
  }
}
