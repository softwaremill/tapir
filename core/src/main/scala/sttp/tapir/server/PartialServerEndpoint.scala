package sttp.tapir.server

import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir._
import sttp.tapir.internal._

import scala.reflect.ClassTag

/** An endpoint with the security logic provided, and the main logic yet unspecified. See [[Endpoint.serverSecurityLogic]].
  *
  * The provided security part of the server logic transforms inputs of type `A`, either to an error of type `E`, or value of type `U`.
  *
  * The part of the server logic which is not provided, will have to transform both `U` and the rest of the input `I` either into an error,
  * or a value of type `O`.
  *
  * Inputs/outputs can be added to partial endpoints as to regular endpoints. The shape of the error outputs can be adjusted in a limited
  * way, by adding new error output variants, similar as if they were defined using [[Tapir.oneOf]]; the variants and the existing error
  * outputs should usually have a common supertype (other than `Any`). Hence, it's possible to create a base, secured input, and then
  * specialise it with inputs, outputs and logic as needed.
  *
  * @tparam A
  *   "Auth": Security input parameter types, which the security logic accepts and returns a `U` or an error `E`.
  * @tparam U
  *   "User": The type of the value returned by the security logic.
  * @tparam I
  *   Input parameter types.
  * @tparam E
  *   Error output parameter types.
  * @tparam O
  *   Output parameter types.
  * @tparam R
  *   The capabilities that are required by this endpoint's inputs/outputs. `Any`, if no requirements.
  * @tparam F
  *   The effect type used in the provided partial server logic.
  */
case class PartialServerEndpoint[A, U, I, E, O, -R, F[_]](
    endpoint: Endpoint[A, I, E, O, R],
    securityLogic: MonadError[F] => A => F[Either[E, U]]
) extends EndpointInputsOps[A, I, E, O, R]
    with EndpointOutputsOps[A, I, E, O, R]
    with EndpointErrorOutputVariantsOps[A, I, E, O, R]
    with EndpointInfoOps[R]
    with EndpointMetaOps { outer =>
  override type ThisType[-_R] = PartialServerEndpoint[A, U, I, E, O, _R, F]
  override type EndpointType[_A, _I, _E, _O, -_R] = PartialServerEndpoint[_A, U, _I, _E, _O, _R, F]

  override def securityInput: EndpointInput[A] = endpoint.securityInput
  override def input: EndpointInput[I] = endpoint.input
  override def errorOutput: EndpointOutput[E] = endpoint.errorOutput
  override def output: EndpointOutput[O] = endpoint.output
  override def info: EndpointInfo = endpoint.info

  override private[tapir] def withInput[I2, R2](input: EndpointInput[I2]): PartialServerEndpoint[A, U, I2, E, O, R with R2, F] =
    copy(endpoint = endpoint.copy(input = input))
  override private[tapir] def withOutput[O2, R2](output: EndpointOutput[O2]) = copy(endpoint = endpoint.copy(output = output))
  override private[tapir] def withErrorOutputVariant[E2, R2](
      errorOutput: EndpointOutput[E2],
      embedE: E => E2
  ): PartialServerEndpoint[A, U, I, E2, O, R with R2, F] =
    this.copy(
      endpoint = endpoint.copy(errorOutput = errorOutput),
      securityLogic = implicit m =>
        a =>
          securityLogic(m)(a).map {
            case Left(e)  => Left(embedE(e))
            case Right(o) => Right(o)
          }
    )
  override private[tapir] def withInfo(info: EndpointInfo) = copy(endpoint = endpoint.copy(info = info))

  override protected def showType: String = "PartialServerEndpoint"

  def serverLogic(f: U => I => F[Either[E, O]]): ServerEndpoint.Full[A, U, I, E, O, R, F] = ServerEndpoint(endpoint, securityLogic, _ => f)

  def serverLogicSuccess(f: U => I => F[O]): ServerEndpoint.Full[A, U, I, E, O, R, F] =
    ServerEndpoint(endpoint, securityLogic, implicit m => u => i => f(u)(i).map(Right(_)))

  def serverLogicError(f: U => I => F[E]): ServerEndpoint.Full[A, U, I, E, O, R, F] =
    ServerEndpoint(endpoint, securityLogic, implicit m => u => i => f(u)(i).map(Left(_)))

  def serverLogicPure(f: U => I => Either[E, O]): ServerEndpoint.Full[A, U, I, E, O, R, F] =
    ServerEndpoint(endpoint, securityLogic, implicit m => u => i => f(u)(i).unit)

  def serverLogicRecoverErrors(
      f: U => I => F[O]
  )(implicit eIsThrowable: E <:< Throwable, eClassTag: ClassTag[E]): ServerEndpoint.Full[A, U, I, E, O, R, F] =
    ServerEndpoint(endpoint, securityLogic, recoverErrors2[U, I, E, O, F](f))
}
