package sttp.tapir.server

import sttp.tapir._
import sttp.tapir.internal._

import scala.reflect.ClassTag

/** Direct-style variant of [[PartialServerEndpoint]], using the [[Id]] "effect". */
case class PartialServerEndpointSync[SECURITY_INPUT, PRINCIPAL, INPUT, ERROR_OUTPUT, OUTPUT, -R](
    endpoint: Endpoint[SECURITY_INPUT, INPUT, ERROR_OUTPUT, OUTPUT, R],
    securityLogic: SECURITY_INPUT => Either[ERROR_OUTPUT, PRINCIPAL]
) extends EndpointInputsOps[SECURITY_INPUT, INPUT, ERROR_OUTPUT, OUTPUT, R]
    with EndpointOutputsOps[SECURITY_INPUT, INPUT, ERROR_OUTPUT, OUTPUT, R]
    with EndpointErrorOutputVariantsOps[SECURITY_INPUT, INPUT, ERROR_OUTPUT, OUTPUT, R]
    with EndpointInfoOps[R]
    with EndpointMetaOps { outer =>
  override type ThisType[-_R] = PartialServerEndpointSync[SECURITY_INPUT, PRINCIPAL, INPUT, ERROR_OUTPUT, OUTPUT, _R]
  override type EndpointType[_A, _I, _E, _O, -_R] = PartialServerEndpointSync[_A, PRINCIPAL, _I, _E, _O, _R]

  override def securityInput: EndpointInput[SECURITY_INPUT] = endpoint.securityInput
  override def input: EndpointInput[INPUT] = endpoint.input
  override def errorOutput: EndpointOutput[ERROR_OUTPUT] = endpoint.errorOutput
  override def output: EndpointOutput[OUTPUT] = endpoint.output
  override def info: EndpointInfo = endpoint.info

  override private[tapir] def withInput[I2, R2](
      input: EndpointInput[I2]
  ): PartialServerEndpointSync[SECURITY_INPUT, PRINCIPAL, I2, ERROR_OUTPUT, OUTPUT, R with R2] =
    copy(endpoint = endpoint.copy(input = input))
  override private[tapir] def withOutput[O2, R2](output: EndpointOutput[O2]) = copy(endpoint = endpoint.copy(output = output))
  override private[tapir] def withErrorOutputVariant[E2, R2](
      errorOutput: EndpointOutput[E2],
      embedE: ERROR_OUTPUT => E2
  ): PartialServerEndpointSync[SECURITY_INPUT, PRINCIPAL, INPUT, E2, OUTPUT, R with R2] =
    this.copy(
      endpoint = endpoint.copy(errorOutput = errorOutput),
      securityLogic = a =>
        securityLogic(a) match {
          case Left(e)  => Left(embedE(e))
          case Right(o) => Right(o)
        }
    )
  override private[tapir] def withInfo(info: EndpointInfo) = copy(endpoint = endpoint.copy(info = info))

  override protected def showType: String = "PartialServerEndpoint"

  def serverLogic(
      f: PRINCIPAL => INPUT => Either[ERROR_OUTPUT, OUTPUT]
  ): ServerEndpoint.Full[SECURITY_INPUT, PRINCIPAL, INPUT, ERROR_OUTPUT, OUTPUT, R, Id] =
    ServerEndpoint[SECURITY_INPUT, PRINCIPAL, INPUT, ERROR_OUTPUT, OUTPUT, R, Id](endpoint, _ => securityLogic, _ => f)

  def serverLogicSuccess(
      f: PRINCIPAL => INPUT => OUTPUT
  ): ServerEndpoint.Full[SECURITY_INPUT, PRINCIPAL, INPUT, ERROR_OUTPUT, OUTPUT, R, Id] =
    ServerEndpoint[SECURITY_INPUT, PRINCIPAL, INPUT, ERROR_OUTPUT, OUTPUT, R, Id](endpoint, _ => securityLogic, _ => u => i => Right(f(u)(i)))

  def serverLogicError(
      f: PRINCIPAL => INPUT => ERROR_OUTPUT
  ): ServerEndpoint.Full[SECURITY_INPUT, PRINCIPAL, INPUT, ERROR_OUTPUT, OUTPUT, R, Id] =
    ServerEndpoint[SECURITY_INPUT, PRINCIPAL, INPUT, ERROR_OUTPUT, OUTPUT, R, Id](endpoint, _ => securityLogic, _ => u => i => Left(f(u)(i)))

  def serverLogicRecoverErrors(
      f: PRINCIPAL => INPUT => OUTPUT
  )(implicit
      eIsThrowable: ERROR_OUTPUT <:< Throwable,
      eClassTag: ClassTag[ERROR_OUTPUT]
  ): ServerEndpoint.Full[SECURITY_INPUT, PRINCIPAL, INPUT, ERROR_OUTPUT, OUTPUT, R, Id] =
    ServerEndpoint[SECURITY_INPUT, PRINCIPAL, INPUT, ERROR_OUTPUT, OUTPUT, R, Id](endpoint, _ => securityLogic, recoverErrors2[PRINCIPAL, INPUT, ERROR_OUTPUT, OUTPUT, Id](f))

  def serverLogicOption(f: PRINCIPAL => INPUT => Option[OUTPUT])(implicit
      eIsUnit: ERROR_OUTPUT =:= Unit
  ): ServerEndpoint.Full[SECURITY_INPUT, PRINCIPAL, INPUT, Unit, OUTPUT, R, Id] =
    ServerEndpoint[SECURITY_INPUT, PRINCIPAL, INPUT, Unit, OUTPUT, R, Id](
      endpoint.asInstanceOf[Endpoint[SECURITY_INPUT, INPUT, Unit, OUTPUT, R]],
      _ => securityLogic.asInstanceOf[SECURITY_INPUT => Either[Unit, PRINCIPAL]],
      _ =>
        u =>
          i =>
            f(u)(i) match {
              case None    => Left(())
              case Some(v) => Right(v)
            }
    )

  /** If the error type is an `Either`, e.g. when using `errorOutEither`, this method accepts server logic that returns either success or
    * the `Right` error type. Use of this method avoids having to wrap the returned error in `Right`.
    */
  def serverLogicRightErrorOrSuccess[LE, RE](
      f: PRINCIPAL => INPUT => Either[RE, OUTPUT]
  )(implicit
      eIsEither: Either[LE, RE] =:= ERROR_OUTPUT
  ): ServerEndpoint.Full[SECURITY_INPUT, PRINCIPAL, INPUT, ERROR_OUTPUT, OUTPUT, R, Id] =
    ServerEndpoint[SECURITY_INPUT, PRINCIPAL, INPUT, ERROR_OUTPUT, OUTPUT, R, Id](
      endpoint,
      _ => securityLogic,
      _ =>
        u =>
          i => {
            f(u)(i) match {
              case Left(e)  => Left(Right(e))
              case Right(r) => Right(r)
            }
          }
    )

  /** If the error type is an `Either`, e.g. when using `errorOutEither`, this method accepts server logic that returns either success or
    * the `Left` error type. Use of this method avoids having to wrap the returned error in `Left`.
    */
  def serverLogicLeftErrorOrSuccess[LE, RE](
      f: PRINCIPAL => INPUT => Either[LE, OUTPUT]
  )(implicit
      eIsEither: Either[LE, RE] =:= ERROR_OUTPUT
  ): ServerEndpoint.Full[SECURITY_INPUT, PRINCIPAL, INPUT, ERROR_OUTPUT, OUTPUT, R, Id] =
    ServerEndpoint[SECURITY_INPUT, PRINCIPAL, INPUT, ERROR_OUTPUT, OUTPUT, R, Id](
      endpoint,
      _ => securityLogic,
      _ =>
        u =>
          i => {
            f(u)(i) match {
              case Left(e)  => Left(Left(e))
              case Right(r) => Right(r)
            }
          }
    )
}
