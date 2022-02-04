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
import zio.ZIO

/** An endpoint with the security logic provided, and the main logic yet unspecified. See [[RichZEndpoint.zServerLogic]].
  *
  * The provided security part of the server logic transforms inputs of type `A`, either to an error of type `E`, or value of type `U`.
  *
  * The part of the server logic which is not provided, will have to transform a tuple: `(U, I)` either into an error, or a value of type
  * `O`.
  *
  * Inputs/outputs can be added to partial endpoints as to regular endpoints, however the shape of the error outputs is fixed and cannot be
  * changed. Hence, it's possible to create a base, secured input, and then specialise it with inputs, outputs and logic as needed.
  *
  * @tparam A
  *   Type of the security inputs, transformed into U
  * @tparam U
  *   Type of transformed security input.
  * @tparam I
  *   Input parameter types.
  * @tparam E
  *   Error output parameter types.
  * @tparam O
  *   Output parameter types.
  * @tparam C
  *   The capabilities that are required by this endpoint's inputs/outputs. `Any`, if no requirements.
  */
case class ZPartialServerEndpoint[R, A, U, I, E, O, -C](endpoint: Endpoint[A, I, E, O, C], securityLogic: A => ZIO[R, E, U])
    extends EndpointInputsOps[A, I, E, O, C]
    with EndpointOutputsOps[A, I, E, O, C]
    with EndpointErrorOutputVariantsOps[A, I, E, O, C]
    with EndpointInfoOps[C]
    with EndpointMetaOps { outer =>

  override type ThisType[-_R] = ZPartialServerEndpoint[R, A, U, I, E, O, _R]
  override type EndpointType[_A, _I, _E, _O, -_R] = ZPartialServerEndpoint[R, _A, U, _I, _E, _O, _R]

  override def securityInput: EndpointInput[A] = endpoint.securityInput
  override def input: EndpointInput[I] = endpoint.input
  def errorOutput: EndpointOutput[E] = endpoint.errorOutput
  override def output: EndpointOutput[O] = endpoint.output
  override def info: EndpointInfo = endpoint.info

  override private[tapir] def withInput[I2, C2](input: EndpointInput[I2]): ZPartialServerEndpoint[R, A, U, I2, E, O, C with C2] =
    copy(endpoint = endpoint.copy(input = input))
  override private[tapir] def withOutput[O2, C2](output: EndpointOutput[O2]) = copy(endpoint = endpoint.copy(output = output))
  override private[tapir] def withInfo(info: EndpointInfo) = copy(endpoint = endpoint.copy(info = info))
  override private[tapir] def withErrorOutputVariant[E2, C2](
      errorOutput: EndpointOutput[E2],
      embedE: E => E2
  ): ZPartialServerEndpoint[R, A, U, I, E2, O, C with C2] =
    this.copy(
      endpoint = endpoint.copy(errorOutput = errorOutput),
      securityLogic = a => securityLogic(a).mapError(embedE)
    )

  override protected def showType: String = "PartialServerEndpoint"

  def serverLogic[R0](logic: U => I => ZIO[R0, E, O]): ZServerEndpoint[R with R0, C] =
    ServerEndpoint(
      endpoint,
      _ => securityLogic(_: A).either.resurrect,
      _ => (u: U) => (i: I) => logic(u)(i).either.resurrect
    )
}
