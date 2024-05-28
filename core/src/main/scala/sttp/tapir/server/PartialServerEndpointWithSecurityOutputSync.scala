package sttp.tapir.server

import sttp.monad.IdentityMonad
import sttp.shared.Identity
import sttp.tapir._
import sttp.tapir.internal._

import scala.reflect.ClassTag

/** Direct-style variant of [[PartialServerEndpointWithSecurityOutput]], using the [[Identity]] "effect". */
case class PartialServerEndpointWithSecurityOutputSync[SECURITY_INPUT, PRINCIPAL, INPUT, ERROR_OUTPUT, SECURITY_OUTPUT, OUTPUT, -R](
    securityOutput: EndpointOutput[SECURITY_OUTPUT],
    endpoint: Endpoint[SECURITY_INPUT, INPUT, ERROR_OUTPUT, OUTPUT, R],
    securityLogic: SECURITY_INPUT => Either[ERROR_OUTPUT, (SECURITY_OUTPUT, PRINCIPAL)]
) extends EndpointInputsOps[SECURITY_INPUT, INPUT, ERROR_OUTPUT, OUTPUT, R]
    with EndpointOutputsOps[SECURITY_INPUT, INPUT, ERROR_OUTPUT, OUTPUT, R]
    with EndpointErrorOutputVariantsOps[SECURITY_INPUT, INPUT, ERROR_OUTPUT, OUTPUT, R]
    with EndpointInfoOps[R]
    with EndpointMetaOps { outer =>
  override type ThisType[-_R] =
    PartialServerEndpointWithSecurityOutputSync[SECURITY_INPUT, PRINCIPAL, INPUT, ERROR_OUTPUT, SECURITY_OUTPUT, OUTPUT, _R]
  override type EndpointType[_A, _I, _E, _O, -_R] =
    PartialServerEndpointWithSecurityOutputSync[_A, PRINCIPAL, _I, _E, SECURITY_OUTPUT, _O, _R]

  override def securityInput: EndpointInput[SECURITY_INPUT] = endpoint.securityInput
  override def input: EndpointInput[INPUT] = endpoint.input
  override def errorOutput: EndpointOutput[ERROR_OUTPUT] = endpoint.errorOutput
  override def output: EndpointOutput[OUTPUT] = endpoint.output
  override def info: EndpointInfo = endpoint.info

  override private[tapir] def withInput[I2, R2](
      input: EndpointInput[I2]
  ): PartialServerEndpointWithSecurityOutputSync[SECURITY_INPUT, PRINCIPAL, I2, ERROR_OUTPUT, SECURITY_OUTPUT, OUTPUT, R with R2] =
    copy(endpoint = endpoint.copy(input = input))
  override private[tapir] def withOutput[O2, R2](output: EndpointOutput[O2]) = copy(endpoint = endpoint.copy(output = output))
  override private[tapir] def withErrorOutputVariant[E2, R2](
      errorOutput: EndpointOutput[E2],
      embedE: ERROR_OUTPUT => E2
  ): PartialServerEndpointWithSecurityOutputSync[SECURITY_INPUT, PRINCIPAL, INPUT, E2, SECURITY_OUTPUT, OUTPUT, R with R2] =
    this.copy(
      endpoint = endpoint.copy(errorOutput = errorOutput),
      securityLogic = a =>
        securityLogic(a) match {
          case Left(e)  => Left(embedE(e))
          case Right(o) => Right(o)
        }
    )
  override private[tapir] def withInfo(info: EndpointInfo) = copy(endpoint = endpoint.copy(info = info))

  override protected def showType: String = "PartialServerEndpointWithSecurityOutput"

  def handle(
      f: PRINCIPAL => INPUT => Either[ERROR_OUTPUT, OUTPUT]
  ): ServerEndpoint.Full[SECURITY_INPUT, (SECURITY_OUTPUT, PRINCIPAL), INPUT, ERROR_OUTPUT, (SECURITY_OUTPUT, OUTPUT), R, Identity] =
    ServerEndpoint[SECURITY_INPUT, (SECURITY_OUTPUT, PRINCIPAL), INPUT, ERROR_OUTPUT, (SECURITY_OUTPUT, OUTPUT), R, Identity](
      endpoint.prependOut(securityOutput),
      _ => securityLogic,
      _ => so_u => i => f(so_u._2)(i).right.map(o => (so_u._1, o))
    )

  def handleSuccess(
      f: PRINCIPAL => INPUT => OUTPUT
  ): ServerEndpoint.Full[SECURITY_INPUT, (SECURITY_OUTPUT, PRINCIPAL), INPUT, ERROR_OUTPUT, (SECURITY_OUTPUT, OUTPUT), R, Identity] =
    ServerEndpoint[SECURITY_INPUT, (SECURITY_OUTPUT, PRINCIPAL), INPUT, ERROR_OUTPUT, (SECURITY_OUTPUT, OUTPUT), R, Identity](
      endpoint.prependOut(securityOutput),
      _ => securityLogic,
      _ => so_u => i => Right((so_u._1, f(so_u._2)(i)))
    )

  def handleError(
      f: PRINCIPAL => INPUT => ERROR_OUTPUT
  ): ServerEndpoint.Full[SECURITY_INPUT, (SECURITY_OUTPUT, PRINCIPAL), INPUT, ERROR_OUTPUT, (SECURITY_OUTPUT, OUTPUT), R, Identity] =
    ServerEndpoint[SECURITY_INPUT, (SECURITY_OUTPUT, PRINCIPAL), INPUT, ERROR_OUTPUT, (SECURITY_OUTPUT, OUTPUT), R, Identity](
      endpoint.prependOut(securityOutput),
      _ => securityLogic,
      _ => so_u => i => Left(f(so_u._2)(i))
    )

  def handleRecoverErrors(
      f: PRINCIPAL => INPUT => OUTPUT
  )(implicit
      eIsThrowable: ERROR_OUTPUT <:< Throwable,
      eClassTag: ClassTag[ERROR_OUTPUT]
  ): ServerEndpoint.Full[SECURITY_INPUT, (SECURITY_OUTPUT, PRINCIPAL), INPUT, ERROR_OUTPUT, (SECURITY_OUTPUT, OUTPUT), R, Identity] =
    ServerEndpoint[SECURITY_INPUT, (SECURITY_OUTPUT, PRINCIPAL), INPUT, ERROR_OUTPUT, (SECURITY_OUTPUT, OUTPUT), R, Identity](
      endpoint.prependOut(securityOutput),
      _ => securityLogic,
      _ =>
        recoverErrors2[(SECURITY_OUTPUT, PRINCIPAL), INPUT, ERROR_OUTPUT, (SECURITY_OUTPUT, OUTPUT), Identity](so_u =>
          i => (so_u._1, f(so_u._2)(i))
        )(
          implicitly,
          implicitly
        )(IdentityMonad)
    )

  def handleOption(f: PRINCIPAL => INPUT => Option[OUTPUT])(implicit
      eIsUnit: ERROR_OUTPUT =:= Unit
  ): ServerEndpoint.Full[SECURITY_INPUT, (SECURITY_OUTPUT, PRINCIPAL), INPUT, Unit, (SECURITY_OUTPUT, OUTPUT), R, Identity] =
    ServerEndpoint[SECURITY_INPUT, (SECURITY_OUTPUT, PRINCIPAL), INPUT, Unit, (SECURITY_OUTPUT, OUTPUT), R, Identity](
      endpoint.prependOut(securityOutput).asInstanceOf[Endpoint[SECURITY_INPUT, INPUT, Unit, (SECURITY_OUTPUT, OUTPUT), R]],
      _ => securityLogic.asInstanceOf[SECURITY_INPUT => Identity[Either[Unit, (SECURITY_OUTPUT, PRINCIPAL)]]],
      _ =>
        so_u =>
          i =>
            f(so_u._2)(i) match {
              case None    => Left(())
              case Some(v) => Right((so_u._1, v))
            }
    )
}
