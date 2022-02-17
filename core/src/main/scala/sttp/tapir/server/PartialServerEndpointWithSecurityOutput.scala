package sttp.tapir.server

import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir._
import sttp.tapir.internal._

import scala.reflect.ClassTag

/** An endpoint with the security logic provided, and the main logic yet unspecified. See [[Endpoint.serverSecurityLogicWithOutput]].
  *
  * The provided security part of the server logic transforms inputs of type `SECURITY_INPUT`, either to an error of type `ERROR_OUTPUT`, or
  * to a tuple consisting of values of types `SECURITY_OUPUT` and `PRINCIPAL`.
  *
  * The part of the server logic which is not provided, will have to transform both `PRINCIPAL` and the rest of the input `INPUT` either
  * into an error, or a value of type `OUTPUT`. The response is then built from `SECURITY_OUTPUT` and `OUTPUT` combined.
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
  * @tparam SECURITY_OUTPUT
  *   Security output parameter types.
  * @tparam OUTPUT
  *   Output parameter types.
  * @tparam R
  *   The capabilities that are required by this endpoint's inputs/outputs. `Any`, if no requirements.
  * @tparam F
  *   The effect type used in the provided partial server logic.
  */
case class PartialServerEndpointWithSecurityOutput[SECURITY_INPUT, PRINCIPAL, INPUT, ERROR_OUTPUT, SECURITY_OUTPUT, OUTPUT, -R, F[_]](
    securityOutput: EndpointOutput[SECURITY_OUTPUT],
    endpoint: Endpoint[SECURITY_INPUT, INPUT, ERROR_OUTPUT, OUTPUT, R],
    securityLogic: MonadError[F] => SECURITY_INPUT => F[Either[ERROR_OUTPUT, (SECURITY_OUTPUT, PRINCIPAL)]]
) extends EndpointInputsOps[SECURITY_INPUT, INPUT, ERROR_OUTPUT, OUTPUT, R]
    with EndpointOutputsOps[SECURITY_INPUT, INPUT, ERROR_OUTPUT, OUTPUT, R]
    with EndpointErrorOutputVariantsOps[SECURITY_INPUT, INPUT, ERROR_OUTPUT, OUTPUT, R]
    with EndpointInfoOps[R]
    with EndpointMetaOps { outer =>
  override type ThisType[-_R] =
    PartialServerEndpointWithSecurityOutput[SECURITY_INPUT, PRINCIPAL, INPUT, ERROR_OUTPUT, SECURITY_OUTPUT, OUTPUT, _R, F]
  override type EndpointType[_A, _I, _E, _O, -_R] =
    PartialServerEndpointWithSecurityOutput[_A, PRINCIPAL, _I, _E, SECURITY_OUTPUT, _O, _R, F]

  override def securityInput: EndpointInput[SECURITY_INPUT] = endpoint.securityInput
  override def input: EndpointInput[INPUT] = endpoint.input
  override def errorOutput: EndpointOutput[ERROR_OUTPUT] = endpoint.errorOutput
  override def output: EndpointOutput[OUTPUT] = endpoint.output
  override def info: EndpointInfo = endpoint.info

  override private[tapir] def withInput[I2, R2](
      input: EndpointInput[I2]
  ): PartialServerEndpointWithSecurityOutput[SECURITY_INPUT, PRINCIPAL, I2, ERROR_OUTPUT, SECURITY_OUTPUT, OUTPUT, R with R2, F] =
    copy(endpoint = endpoint.copy(input = input))
  override private[tapir] def withOutput[O2, R2](output: EndpointOutput[O2]) = copy(endpoint = endpoint.copy(output = output))
  override private[tapir] def withErrorOutputVariant[E2, R2](
      errorOutput: EndpointOutput[E2],
      embedE: ERROR_OUTPUT => E2
  ): PartialServerEndpointWithSecurityOutput[SECURITY_INPUT, PRINCIPAL, INPUT, E2, SECURITY_OUTPUT, OUTPUT, R with R2, F] =
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

  override protected def showType: String = "PartialServerEndpointWithSecurityOutput"

  def serverLogic(
      f: PRINCIPAL => INPUT => F[Either[ERROR_OUTPUT, OUTPUT]]
  ): ServerEndpoint.Full[SECURITY_INPUT, (SECURITY_OUTPUT, PRINCIPAL), INPUT, ERROR_OUTPUT, (SECURITY_OUTPUT, OUTPUT), R, F] =
    ServerEndpoint(
      endpoint.prependOut(securityOutput),
      securityLogic,
      implicit m => so_u => i => f(so_u._2)(i).map(_.right.map(o => (so_u._1, o)))
    )

  def serverLogicSuccess(
      f: PRINCIPAL => INPUT => F[OUTPUT]
  ): ServerEndpoint.Full[SECURITY_INPUT, (SECURITY_OUTPUT, PRINCIPAL), INPUT, ERROR_OUTPUT, (SECURITY_OUTPUT, OUTPUT), R, F] =
    ServerEndpoint(
      endpoint.prependOut(securityOutput),
      securityLogic,
      implicit m => so_u => i => f(so_u._2)(i).map(o => Right((so_u._1, o)))
    )

  def serverLogicError(
      f: PRINCIPAL => INPUT => F[ERROR_OUTPUT]
  ): ServerEndpoint.Full[SECURITY_INPUT, (SECURITY_OUTPUT, PRINCIPAL), INPUT, ERROR_OUTPUT, (SECURITY_OUTPUT, OUTPUT), R, F] =
    ServerEndpoint(endpoint.prependOut(securityOutput), securityLogic, implicit m => so_u => i => f(so_u._2)(i).map(Left(_)))

  def serverLogicPure(
      f: PRINCIPAL => INPUT => Either[ERROR_OUTPUT, OUTPUT]
  ): ServerEndpoint.Full[SECURITY_INPUT, (SECURITY_OUTPUT, PRINCIPAL), INPUT, ERROR_OUTPUT, (SECURITY_OUTPUT, OUTPUT), R, F] =
    ServerEndpoint(
      endpoint.prependOut(securityOutput),
      securityLogic,
      implicit m => so_u => i => f(so_u._2)(i).right.map(o => (so_u._1, o)).unit
    )

  def serverLogicRecoverErrors(
      f: PRINCIPAL => INPUT => F[OUTPUT]
  )(implicit
      eIsThrowable: ERROR_OUTPUT <:< Throwable,
      eClassTag: ClassTag[ERROR_OUTPUT]
  ): ServerEndpoint.Full[SECURITY_INPUT, (SECURITY_OUTPUT, PRINCIPAL), INPUT, ERROR_OUTPUT, (SECURITY_OUTPUT, OUTPUT), R, F] =
    ServerEndpoint(
      endpoint.prependOut(securityOutput),
      securityLogic,
      implicit m =>
        recoverErrors2[(SECURITY_OUTPUT, PRINCIPAL), INPUT, ERROR_OUTPUT, (SECURITY_OUTPUT, OUTPUT), F](so_u =>
          i => f(so_u._2)(i).map(o => (so_u._1, o))
        )(
          implicitly,
          implicitly
        )(m)
    )

  def serverLogicOption(f: PRINCIPAL => INPUT => F[Option[OUTPUT]])(implicit
      eIsUnit: ERROR_OUTPUT =:= Unit
  ): ServerEndpoint.Full[SECURITY_INPUT, (SECURITY_OUTPUT, PRINCIPAL), INPUT, Unit, (SECURITY_OUTPUT, OUTPUT), R, F] =
    ServerEndpoint(
      endpoint.prependOut(securityOutput).asInstanceOf[Endpoint[SECURITY_INPUT, INPUT, Unit, (SECURITY_OUTPUT, OUTPUT), R]],
      securityLogic.asInstanceOf[MonadError[F] => SECURITY_INPUT => F[Either[Unit, (SECURITY_OUTPUT, PRINCIPAL)]]],
      implicit m =>
        so_u =>
          i =>
            f(so_u._2)(i).map {
              case None    => Left(())
              case Some(v) => Right((so_u._1, v))
            }
    )
}
