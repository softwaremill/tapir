package sttp.tapir.ztapir

import sttp.tapir.internal.{ParamsAsAny, mkCombine, _}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.typelevel.ParamConcat
import sttp.tapir.{EndpointInfo, EndpointInfoOps, EndpointInput, EndpointInputsOps, EndpointMetaOps, EndpointOutput, EndpointOutputsOps}
import zio.ZIO

/**
  * An endpoint, with some of the server logic already provided, and some left unspecified.
  * See [[RichZEndpoint.zServerLogicForCurrent]].
  *
  * The part of the server logic which is provided transforms some inputs either to an error of type `E`, or value of
  * type `U`.
  *
  * The part of the server logic which is not provided, transforms a tuple: `(U, I)` either into an error, or a value
  * of type `O`.
  *
  * Inputs/outputs can be added to partial endpoints as to regular endpoints, however the shape of the error outputs
  * is fixed and cannot be changed.
  *
  * @tparam R The environment needed by the partial server logic.
  * @tparam U Type of partially transformed input.
  * @tparam I Input parameter types.
  * @tparam E Error output parameter types.
  * @tparam O Output parameter types.
  */
abstract class ZPartialServerEndpoint[R, U, I, E, O](val endpoint: ZEndpoint[I, E, O])
    extends EndpointInputsOps[I, E, O, Nothing]
    with EndpointOutputsOps[I, E, O, Nothing]
    with EndpointInfoOps[I, E, O, Nothing]
    with EndpointMetaOps[I, E, O, Nothing] { outer =>
  // original type of the partial input (transformed into U)
  type T
  protected def tInput: EndpointInput[T]
  protected def partialLogic: T => ZIO[R, E, U]

  override type EndpointType[_I, _E, _O, +_S] = ZPartialServerEndpoint[R, U, _I, _E, _O]

  override def input: EndpointInput[I] = endpoint.input
  def errorOutput: EndpointOutput[E] = endpoint.errorOutput
  override def output: EndpointOutput[O] = endpoint.output
  override def info: EndpointInfo = endpoint.info

  private def withEndpoint[I2, O2, S2 >: Nothing](e2: ZEndpoint[I2, E, O2]): ZPartialServerEndpoint[R, U, I2, E, O2] =
    new ZPartialServerEndpoint[R, U, I2, E, O2](e2) {
      override type T = outer.T
      override protected def tInput: EndpointInput[T] = outer.tInput
      override protected def partialLogic: T => ZIO[R, E, U] = outer.partialLogic
    }
  override private[tapir] def withInput[I2, S2 >: Nothing](input: EndpointInput[I2]): ZPartialServerEndpoint[R, U, I2, E, O] =
    withEndpoint(endpoint.withInput(input))
  override private[tapir] def withOutput[O2, S2 >: Nothing](output: EndpointOutput[O2]) = withEndpoint(endpoint.withOutput(output))
  override private[tapir] def withInfo(info: EndpointInfo) = withEndpoint(endpoint.withInfo(info))

  override protected def additionalInputsForShow: Vector[EndpointInput.Basic[_]] = tInput.asVectorOfBasicInputs()
  override protected def showType: String = "PartialServerEndpoint"

  def serverLogicForCurrent[V, UV](
      f: I => ZIO[R, E, V]
  )(implicit concat: ParamConcat.Aux[U, V, UV]): ZPartialServerEndpoint[R, UV, Unit, E, O] =
    new ZPartialServerEndpoint[R, UV, Unit, E, O](endpoint.copy(input = emptyInput)) {
      override type T = (outer.T, I)
      override def tInput: EndpointInput[(outer.T, I)] = outer.tInput.and(outer.endpoint.input)
      override def partialLogic: ((outer.T, I)) => ZIO[R, E, UV] = {
        case (t, i) =>
          outer.partialLogic(t).flatMap { u =>
            f(i).map { v =>
              mkCombine(concat).apply(ParamsAsAny(u), ParamsAsAny(v)).asAny.asInstanceOf[UV]
            }
          }
      }
    }

  def serverLogic(g: ((U, I)) => ZIO[R, E, O]): ZServerEndpoint[R, (T, I), E, O] =
    ServerEndpoint(
      endpoint.prependIn(tInput): ZEndpoint[(T, I), E, O],
      _ => {
        case (t, i) =>
          partialLogic(t).flatMap(u => g((u, i))).either
      }
    )
}
