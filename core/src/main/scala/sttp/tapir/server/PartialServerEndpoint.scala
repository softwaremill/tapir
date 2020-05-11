package sttp.tapir.server

import sttp.tapir.typelevel.ParamConcat
import sttp.tapir._
import sttp.tapir.internal._
import sttp.tapir.monad.MonadError
import sttp.tapir.monad.syntax._

/**
  * An endpoint, with some of the server logic already provided, and some left unspecified.
  * See [[Endpoint.serverLogicForCurrent]].
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
  * @tparam U Type of partially transformed input.
  * @tparam I Input parameter types.
  * @tparam E Error output parameter types.
  * @tparam O Output parameter types.
  * @tparam S The type of streams that are used by this endpoint's inputs/outputs. `Nothing`, if no streams are used.
  * @tparam F The effect type used in the provided partial server logic.
  */
abstract class PartialServerEndpoint[U, I, E, O, +S, F[_]](val endpoint: Endpoint[I, E, O, S])
    extends EndpointInputsOps[I, E, O, S]
    with EndpointOutputsOps[I, E, O, S]
    with EndpointInfoOps[I, E, O, S]
    with EndpointMetaOps[I, E, O, S] { outer =>
  // original type of the partial input (transformed into U)
  type T
  protected def tInput: EndpointInput[T]
  protected def partialLogic: MonadError[F] => T => F[Either[E, U]]

  override type EndpointType[_I, _E, _O, +_S] = PartialServerEndpoint[U, _I, _E, _O, _S, F]

  override def input: EndpointInput[I] = endpoint.input
  def errorOutput: EndpointOutput[E] = endpoint.errorOutput
  override def output: EndpointOutput[O] = endpoint.output
  override def info: EndpointInfo = endpoint.info

  private def withEndpoint[I2, O2, S2 >: S](e2: Endpoint[I2, E, O2, S2]): PartialServerEndpoint[U, I2, E, O2, S2, F] =
    new PartialServerEndpoint[U, I2, E, O2, S2, F](e2) {
      override type T = outer.T
      override protected def tInput: EndpointInput[T] = outer.tInput
      override protected def partialLogic: MonadError[F] => T => F[Either[E, U]] = outer.partialLogic
    }
  override private[tapir] def withInput[I2, S2 >: S](input: EndpointInput[I2]): PartialServerEndpoint[U, I2, E, O, S2, F] =
    withEndpoint(endpoint.withInput(input))
  override private[tapir] def withOutput[O2, S2 >: S](output: EndpointOutput[O2]) = withEndpoint(endpoint.withOutput(output))
  override private[tapir] def withInfo(info: EndpointInfo) = withEndpoint(endpoint.withInfo(info))

  override protected def additionalInputsForShow: Vector[EndpointInput.Basic[_]] = tInput.asVectorOfBasicInputs()
  override protected def showType: String = "PartialServerEndpoint"

  def serverLogicForCurrent[V, UV](
      _f: I => F[Either[E, V]]
  )(implicit concat: ParamConcat.Aux[U, V, UV]): PartialServerEndpoint[UV, Unit, E, O, S, F] =
    new PartialServerEndpoint[UV, Unit, E, O, S, F](endpoint.copy(input = emptyInput)) {
      override type T = (outer.T, I)
      override def tInput: EndpointInput[(outer.T, I)] = outer.tInput.and(outer.endpoint.input)
      override def partialLogic: MonadError[F] => ((outer.T, I)) => F[Either[E, UV]] =
        implicit monad => {
          case (t, i) =>
            outer.partialLogic(monad)(t).flatMap {
              case Left(e) => (Left(e): Either[E, UV]).unit
              case Right(u) =>
                _f(i).map {
                  _.map(v => mkCombine(concat).apply(ParamsAsAny(u), ParamsAsAny(v)).asAny.asInstanceOf[UV])
                }
            }
        }
    }

  def serverLogic(g: ((U, I)) => F[Either[E, O]]): ServerEndpoint[(T, I), E, O, S, F] =
    ServerEndpoint[(T, I), E, O, S, F](
      endpoint.prependIn(tInput): Endpoint[(T, I), E, O, S],
      (m: MonadError[F]) => {
        case (t, i) =>
          implicit val monad: MonadError[F] = m
          partialLogic(monad)(t).flatMap {
            case Left(e)  => (Left(e): Either[E, O]).unit
            case Right(u) => g((u, i))
          }
      }
    )
}
