package sttp.tapir.ztapir

import sttp.tapir.server.ServerEndpoint
import sttp.tapir.{
  Endpoint,
  EndpointErrorOutputVariantsOps,
  EndpointInfo,
  EndpointInfoOps,
  EndpointInput,
  EndpointInputsOps,
  EndpointMetaOps,
  EndpointOutput,
  EndpointOutputsOps
}
import zio.{RIO, ZIO}

/** An endpoint with the security logic provided, and the main logic yet unspecified. See [[RichZEndpoint.zServerLogic]].
  *
  * The provided security part of the server logic transforms inputs of type `SECURITY_INPUT`, either to an error of type `ERROR_OUTPUT`, or
  * value of type `PRINCIPAL`.
  *
  * The part of the server logic which is not provided, will have to transform a tuple: `(PRINCIPAL, INPUT)` either into an error, or a
  * value of type `OUTPUT`.
  *
  * Inputs/outputs can be added to partial endpoints as to regular endpoints, however the shape of the error outputs is fixed and cannot be
  * changed. Hence, it's possible to create a base, secured input, and then specialise it with inputs, outputs and logic as needed.
  *
  * @tparam SECURITY_INPUT
  *   Type of the security inputs, transformed into PRINCIPAL
  * @tparam PRINCIPAL
  *   Type of transformed security input.
  * @tparam INPUT
  *   Input parameter types.
  * @tparam ERROR_OUTPUT
  *   Error output parameter types.
  * @tparam OUTPUT
  *   Output parameter types.
  * @tparam C
  *   The capabilities that are required by this endpoint's inputs/outputs. `Any`, if no requirements.
  */
case class ZPartialServerEndpoint[R, SECURITY_INPUT, PRINCIPAL, INPUT, ERROR_OUTPUT, OUTPUT, -C](
    endpoint: Endpoint[SECURITY_INPUT, INPUT, ERROR_OUTPUT, OUTPUT, C],
    securityLogic: SECURITY_INPUT => ZIO[R, ERROR_OUTPUT, PRINCIPAL]
) extends EndpointInputsOps[SECURITY_INPUT, INPUT, ERROR_OUTPUT, OUTPUT, C]
    with EndpointOutputsOps[SECURITY_INPUT, INPUT, ERROR_OUTPUT, OUTPUT, C]
    with EndpointErrorOutputVariantsOps[SECURITY_INPUT, INPUT, ERROR_OUTPUT, OUTPUT, C]
    with EndpointInfoOps[C]
    with EndpointMetaOps { outer =>

  override type ThisType[-_R] = ZPartialServerEndpoint[R, SECURITY_INPUT, PRINCIPAL, INPUT, ERROR_OUTPUT, OUTPUT, _R]
  override type EndpointType[_A, _I, _E, _O, -_R] = ZPartialServerEndpoint[R, _A, PRINCIPAL, _I, _E, _O, _R]

  override def securityInput: EndpointInput[SECURITY_INPUT] = endpoint.securityInput
  override def input: EndpointInput[INPUT] = endpoint.input
  def errorOutput: EndpointOutput[ERROR_OUTPUT] = endpoint.errorOutput
  override def output: EndpointOutput[OUTPUT] = endpoint.output
  override def info: EndpointInfo = endpoint.info

  override private[tapir] def withInput[I2, C2](
      input: EndpointInput[I2]
  ): ZPartialServerEndpoint[R, SECURITY_INPUT, PRINCIPAL, I2, ERROR_OUTPUT, OUTPUT, C with C2] =
    copy(endpoint = endpoint.copy(input = input))
  override private[tapir] def withOutput[O2, C2](output: EndpointOutput[O2]) = copy(endpoint = endpoint.copy(output = output))
  override private[tapir] def withInfo(info: EndpointInfo) = copy(endpoint = endpoint.copy(info = info))
  override private[tapir] def withErrorOutputVariant[E2, C2](
      errorOutput: EndpointOutput[E2],
      embedE: ERROR_OUTPUT => E2
  ): ZPartialServerEndpoint[R, SECURITY_INPUT, PRINCIPAL, INPUT, E2, OUTPUT, C with C2] =
    this.copy(
      endpoint = endpoint.copy(errorOutput = errorOutput),
      securityLogic = a => securityLogic(a).mapError(embedE)
    )

  override protected def showType: String = "PartialServerEndpoint"

  def serverLogic[R0](
      logic: PRINCIPAL => INPUT => ZIO[R0, ERROR_OUTPUT, OUTPUT]
  ): ServerEndpoint.Full[SECURITY_INPUT, PRINCIPAL, INPUT, ERROR_OUTPUT, OUTPUT, C, RIO[R with R0, *]] =
    ServerEndpoint(
      endpoint,
      _ => securityLogic(_: SECURITY_INPUT).either.resurrect,
      _ => (u: PRINCIPAL) => (i: INPUT) => logic(u)(i).either.resurrect
    )
}
