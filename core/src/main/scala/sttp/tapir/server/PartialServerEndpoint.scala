package sttp.tapir.server

import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir._
import sttp.tapir.internal._

import scala.reflect.ClassTag

/** An endpoint with the security logic provided, and the main logic yet unspecified. See [[Endpoint.serverSecurityLogic]].
  *
  * The provided security part of the server logic transforms inputs of type `SECURITY_INPUT`, either to an error of type `ERROR_OUTPUT`, or
  * value of type `PRINCIPAL`.
  *
  * The part of the server logic which is not provided, will have to transform both `PRINCIPAL` and the rest of the input `INPUT` either
  * into an error, or a value of type `OUTPUT`.
  *
  * Inputs/outputs can be added to partial endpoints as to regular endpoints. The shape of the error outputs can be adjusted in a limited
  * way, by adding new error output variants, similar as if they were defined using [[Tapir.oneOf]]; the variants and the existing error
  * outputs should usually have a common supertype (other than `Any`). Hence, it's possible to create a base, secured input, and then
  * specialise it with inputs, outputs and logic as needed.
  *
  * @tparam SECURITY_INPUT
  *   Security input parameter types, which the security logic accepts and returns a `PRINCIPAL` or an error `ERROR_OUTPUT`.
  * @tparam PRINCIPAL
  *   The type of the value returned by the security logic.
  * @tparam INPUT
  *   Input parameter types.
  * @tparam ERROR_OUTPUT
  *   Error output parameter types.
  * @tparam OUTPUT
  *   Output parameter types.
  * @tparam R
  *   The capabilities that are required by this endpoint's inputs/outputs. `Any`, if no requirements.
  * @tparam F
  *   The effect type used in the provided partial server logic.
  */
case class PartialServerEndpoint[SECURITY_INPUT, PRINCIPAL, INPUT, ERROR_OUTPUT, OUTPUT, -R, F[_]](
    endpoint: Endpoint[SECURITY_INPUT, INPUT, ERROR_OUTPUT, OUTPUT, R],
    securityLogic: MonadError[F] => SECURITY_INPUT => F[Either[ERROR_OUTPUT, PRINCIPAL]]
) extends EndpointInputsOps[SECURITY_INPUT, INPUT, ERROR_OUTPUT, OUTPUT, R]
    with EndpointOutputsOps[SECURITY_INPUT, INPUT, ERROR_OUTPUT, OUTPUT, R]
    with EndpointErrorOutputVariantsOps[SECURITY_INPUT, INPUT, ERROR_OUTPUT, OUTPUT, R]
    with EndpointInfoOps[R]
    with EndpointMetaOps { outer =>
  override type ThisType[-_R] = PartialServerEndpoint[SECURITY_INPUT, PRINCIPAL, INPUT, ERROR_OUTPUT, OUTPUT, _R, F]
  override type EndpointType[_A, _I, _E, _O, -_R] = PartialServerEndpoint[_A, PRINCIPAL, _I, _E, _O, _R, F]

  override def securityInput: EndpointInput[SECURITY_INPUT] = endpoint.securityInput
  override def input: EndpointInput[INPUT] = endpoint.input
  override def errorOutput: EndpointOutput[ERROR_OUTPUT] = endpoint.errorOutput
  override def output: EndpointOutput[OUTPUT] = endpoint.output
  override def info: EndpointInfo = endpoint.info

  override private[tapir] def withInput[I2, R2](
      input: EndpointInput[I2]
  ): PartialServerEndpoint[SECURITY_INPUT, PRINCIPAL, I2, ERROR_OUTPUT, OUTPUT, R with R2, F] =
    copy(endpoint = endpoint.copy(input = input))
  override private[tapir] def withOutput[O2, R2](output: EndpointOutput[O2]) = copy(endpoint = endpoint.copy(output = output))
  override private[tapir] def withErrorOutputVariant[E2, R2](
      errorOutput: EndpointOutput[E2],
      embedE: ERROR_OUTPUT => E2
  ): PartialServerEndpoint[SECURITY_INPUT, PRINCIPAL, INPUT, E2, OUTPUT, R with R2, F] =
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

  def serverLogic(
      f: PRINCIPAL => INPUT => F[Either[ERROR_OUTPUT, OUTPUT]]
  ): ServerEndpoint.Full[SECURITY_INPUT, PRINCIPAL, INPUT, ERROR_OUTPUT, OUTPUT, R, F] = ServerEndpoint(endpoint, securityLogic, _ => f)

  def serverLogicSuccess(
      f: PRINCIPAL => INPUT => F[OUTPUT]
  ): ServerEndpoint.Full[SECURITY_INPUT, PRINCIPAL, INPUT, ERROR_OUTPUT, OUTPUT, R, F] =
    ServerEndpoint(endpoint, securityLogic, implicit m => u => i => f(u)(i).map(Right(_)))

  def serverLogicError(
      f: PRINCIPAL => INPUT => F[ERROR_OUTPUT]
  ): ServerEndpoint.Full[SECURITY_INPUT, PRINCIPAL, INPUT, ERROR_OUTPUT, OUTPUT, R, F] =
    ServerEndpoint(endpoint, securityLogic, implicit m => u => i => f(u)(i).map(Left(_)))

  def serverLogicPure(
      f: PRINCIPAL => INPUT => Either[ERROR_OUTPUT, OUTPUT]
  ): ServerEndpoint.Full[SECURITY_INPUT, PRINCIPAL, INPUT, ERROR_OUTPUT, OUTPUT, R, F] =
    ServerEndpoint(endpoint, securityLogic, implicit m => u => i => f(u)(i).unit)

  def serverLogicRecoverErrors(
      f: PRINCIPAL => INPUT => F[OUTPUT]
  )(implicit
      eIsThrowable: ERROR_OUTPUT <:< Throwable,
      eClassTag: ClassTag[ERROR_OUTPUT]
  ): ServerEndpoint.Full[SECURITY_INPUT, PRINCIPAL, INPUT, ERROR_OUTPUT, OUTPUT, R, F] =
    ServerEndpoint(endpoint, securityLogic, recoverErrors2[PRINCIPAL, INPUT, ERROR_OUTPUT, OUTPUT, F](f))

  def serverLogicOption(f: PRINCIPAL => INPUT => F[Option[OUTPUT]])(implicit
      eIsUnit: ERROR_OUTPUT =:= Unit
  ): ServerEndpoint.Full[SECURITY_INPUT, PRINCIPAL, INPUT, Unit, OUTPUT, R, F] =
    ServerEndpoint(
      endpoint.asInstanceOf[Endpoint[SECURITY_INPUT, INPUT, Unit, OUTPUT, R]],
      securityLogic.asInstanceOf[MonadError[F] => SECURITY_INPUT => F[Either[Unit, PRINCIPAL]]],
      implicit m =>
        u =>
          i =>
            f(u)(i).map {
              case None    => Left(())
              case Some(v) => Right(v)
            }
    )
}
