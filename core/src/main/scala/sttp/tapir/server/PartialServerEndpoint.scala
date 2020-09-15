package sttp.tapir.server

import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.typelevel.ParamConcat
import sttp.tapir._
import sttp.tapir.internal._

import scala.reflect.ClassTag

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
  * @tparam R The capabilities that are required by this endpoint's inputs/outputs. `Any`, if no requirements.
  * @tparam F The effect type used in the provided partial server logic.
  */
abstract class PartialServerEndpoint[U, I, E, O, -R, F[_]](partialEndpoint: Endpoint[I, E, O, R])
    extends EndpointInputsOps[I, E, O, R]
    with EndpointOutputsOps[I, E, O, R]
    with EndpointInfoOps[I, E, O, R]
    with EndpointMetaOps[I, E, O, R] { outer =>
  // original type of the partial input (transformed into U)
  type T
  protected def tInput: EndpointInput[T]
  protected def partialLogic: MonadError[F] => T => F[Either[E, U]]

  override type EndpointType[_I, _E, _O, -_R] = PartialServerEndpoint[U, _I, _E, _O, _R, F]

  def endpoint: Endpoint[(T, I), E, O, R] = partialEndpoint.prependIn(tInput)

  override def input: EndpointInput[I] = partialEndpoint.input
  def errorOutput: EndpointOutput[E] = partialEndpoint.errorOutput
  override def output: EndpointOutput[O] = partialEndpoint.output
  override def info: EndpointInfo = partialEndpoint.info

  private def withEndpoint[I2, O2, R2 <: R](e2: Endpoint[I2, E, O2, R2]): PartialServerEndpoint[U, I2, E, O2, R2, F] =
    new PartialServerEndpoint[U, I2, E, O2, R2, F](e2) {
      override type T = outer.T
      override protected def tInput: EndpointInput[T] = outer.tInput
      override protected def partialLogic: MonadError[F] => T => F[Either[E, U]] = outer.partialLogic
    }
  override private[tapir] def withInput[I2, R2](input: EndpointInput[I2]): PartialServerEndpoint[U, I2, E, O, R with R2, F] =
    withEndpoint(partialEndpoint.withInput(input))
  override private[tapir] def withOutput[O2, R2](output: EndpointOutput[O2]) = withEndpoint(partialEndpoint.withOutput(output))
  override private[tapir] def withInfo(info: EndpointInfo) = withEndpoint(partialEndpoint.withInfo(info))

  override protected def additionalInputsForShow: Vector[EndpointInput.Basic[_]] = tInput.asVectorOfBasicInputs()
  override protected def showType: String = "PartialServerEndpoint"

  def serverLogicForCurrent[V, UV](
      f: I => F[Either[E, V]]
  )(implicit concat: ParamConcat.Aux[U, V, UV]): PartialServerEndpoint[UV, Unit, E, O, R, F] = serverLogicForCurrentM(_ => f)

  def serverLogicForCurrentRecoverErrors[V, UV](
      f: I => F[V]
  )(implicit
      concat: ParamConcat.Aux[U, V, UV],
      eIsThrowable: E <:< Throwable,
      eClassTag: ClassTag[E]
  ): PartialServerEndpoint[UV, Unit, E, O, R, F] =
    serverLogicForCurrentM(recoverErrors(f))

  private def serverLogicForCurrentM[V, UV](
      _f: MonadError[F] => I => F[Either[E, V]]
  )(implicit concat: ParamConcat.Aux[U, V, UV]): PartialServerEndpoint[UV, Unit, E, O, R, F] =
    new PartialServerEndpoint[UV, Unit, E, O, R, F](partialEndpoint.copy(input = emptyInput)) {
      override type T = (outer.T, I)
      override def tInput: EndpointInput[(outer.T, I)] = outer.tInput.and(outer.partialEndpoint.input)
      override def partialLogic: MonadError[F] => ((outer.T, I)) => F[Either[E, UV]] =
        implicit monad => { case (t, i) =>
          outer.partialLogic(monad)(t).flatMap {
            case Left(e) => (Left(e): Either[E, UV]).unit
            case Right(u) =>
              _f(monad)(i).map {
                _.map(v => mkCombine(concat).apply(ParamsAsAny(u), ParamsAsAny(v)).asAny.asInstanceOf[UV])
              }
          }
        }
    }

  def serverLogic(g: ((U, I)) => F[Either[E, O]]): ServerEndpoint[(T, I), E, O, R, F] = serverLogicM(_ => g)

  def serverLogicRecoverErrors(
      g: ((U, I)) => F[O]
  )(implicit eIsThrowable: E <:< Throwable, eClassTag: ClassTag[E]): ServerEndpoint[(T, I), E, O, R, F] =
    serverLogicM(recoverErrors(g))

  private def serverLogicM(g: MonadError[F] => ((U, I)) => F[Either[E, O]]): ServerEndpoint[(T, I), E, O, R, F] =
    ServerEndpoint[(T, I), E, O, R, F](
      endpoint,
      (m: MonadError[F]) => { case (t, i) =>
        implicit val monad: MonadError[F] = m
        partialLogic(monad)(t).flatMap {
          case Left(e)  => (Left(e): Either[E, O]).unit
          case Right(u) => g(m)((u, i))
        }
      }
    )
}
