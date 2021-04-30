package sttp.tapir.ztapir

import sttp.tapir.{EndpointInfo, EndpointInfoOps, EndpointInput, EndpointMetaOps, EndpointOutput}
import sttp.tapir.internal.{combine, split}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.typelevel.{ParamConcat, ParamSubtract}
import zio.ZIO

/** An endpoint description together with partial server logic. See [[RichZEndpoint.zServerLogicPart]].
  *
  * The part of the server logic which is provided transforms some inputs either to an error of type `E`, or value of
  * type `U`.
  *
  * The part of the server logic which is not provided, transforms a tuple: `(U, J)` either into an error of type `E`,
  * or a value of type `O`.
  *
  * @tparam R The environment needed by the partial server logic.
  * @tparam U The type of the value returned by the partial server logic.
  * @tparam J Remaining input parameter types, for which logic has yet to be provided.
  * @tparam I Entire input parameter types. `I = T + J`, where `T` is the part of the input consumed by the partial
  *           logic, and converted to `U`.
  * @tparam E Error output parameter types.
  * @tparam O Output parameter types.
  */
abstract class ZServerEndpointInParts[R, U, J, I, E, O](val endpoint: ZEndpoint[I, E, O])
    extends EndpointInfoOps[I, E, O, Nothing]
    with EndpointMetaOps[I, E, O, Nothing] { outer =>

  /** Part of the input, consumed by `logicFragment`.
    */
  protected type T
  protected def splitInput: I => (T, J)
  protected def logicFragment: T => ZIO[R, E, U]

  override type EndpointType[_I, _E, _O, -_R] = ZServerEndpointInParts[R, U, J, _I, _E, _O]
  override def input: EndpointInput[I] = endpoint.input
  override def errorOutput: EndpointOutput[E] = endpoint.errorOutput
  override def output: EndpointOutput[O] = endpoint.output
  override def info: EndpointInfo = endpoint.info

  override private[tapir] def withInfo(info: EndpointInfo): ZServerEndpointInParts[R, U, J, I, E, O] =
    new ZServerEndpointInParts[R, U, J, I, E, O](endpoint.info(info)) {
      override type T = outer.T
      override def splitInput: I => (outer.T, J) = outer.splitInput
      override def logicFragment: T => ZIO[R, E, U] = outer.logicFragment
    }

  override protected def showType: String = "FragmentedServerEndpoint"

  /** Complete the server logic for this endpoint, given the result of applying the partial server logic, and
    * the remaining input.
    */
  def andThen[R2 <: R](remainingLogic: ((U, J)) => ZIO[R2, E, O]): ZServerEndpoint[R2, I, E, O] =
    ServerEndpoint(
      endpoint,
      { _ => i =>
        {
          val (t, j): (T, J) = splitInput(i)
          logicFragment(t).flatMap(u => remainingLogic((u, j))).either.resurrect
        }
      }
    )

  /** Define logic for some part of the remaining input. The result will be an server endpoint, which will need to be
    * completed with a function accepting as arguments outputs of both previous and this server logic parts, and
    * the input.
    */
  def andThenPart[R2 <: R, T2, J2, V, UV](
      nextPart: T2 => ZIO[R2, E, V]
  )(implicit
      jMinusT2: ParamSubtract.Aux[J, T2, J2],
      uu2Concat: ParamConcat.Aux[U, V, UV]
  ): ZServerEndpointInParts[R2, UV, J2, I, E, O] =
    new ZServerEndpointInParts[R2, UV, J2, I, E, O](endpoint) {
      override type T = (outer.T, T2)

      override def splitInput: I => ((outer.T, T2), J2) =
        i => {
          val (t, r) = outer.splitInput(i)
          val (t2, r2) = split(r)(jMinusT2)
          ((t, t2), r2)
        }

      override def logicFragment: T => ZIO[R2, E, UV] = { case (t, t2) =>
        outer.logicFragment(t).flatMap { u =>
          nextPart(t2).map { u2 =>
            combine(u, u2)(uu2Concat)
          }
        }
      }
    }
}
