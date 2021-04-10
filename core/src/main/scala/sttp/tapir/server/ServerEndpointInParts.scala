package sttp.tapir.server

import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.{Endpoint, EndpointInfo, EndpointInfoOps, EndpointInput, EndpointMetaOps, EndpointOutput}
import sttp.tapir.typelevel.{ParamConcat, ParamSubtract}
import sttp.tapir.internal._

import scala.reflect.ClassTag

/** An endpoint description together with partial server logic. See [[Endpoint.serverLogicPart]].
  *
  * The part of the server logic which is provided transforms some inputs either to an error of type `E`, or value of
  * type `U`.
  *
  * The part of the server logic which is not provided, transforms a tuple: `(U, R)` either into an error of type `E`,
  * or a value of type `O`.
  *
  * @tparam U The type of the value returned by the partial server logic.
  * @tparam IR Remaining input parameter types, for which logic has yet to be provided.
  * @tparam E Error output parameter types.
  * @tparam O Output parameter types.
  * @tparam R The capabilities that are required by this endpoint's inputs/outputs. `Any`, if no requirements.
  * @tparam F The effect type used in the provided server logic.
  */
abstract class ServerEndpointInParts[U, IR, E, O, -R, F[_]] extends EndpointInfoOps[R] with EndpointMetaOps { outer =>

  /** Entire input parameter types. `I = T + IR`, where `T` is the part of the input consumed by the partial logic, and converted to `U`. */
  protected type I

  /** Part of the input, consumed by `logicFragment`. */
  protected type T

  def endpoint: Endpoint[I, E, O, R]

  protected def splitInput: I => (T, IR)
  protected def logicFragment: MonadError[F] => T => F[Either[E, U]]

  override type ThisType[-_R] = ServerEndpointInParts[U, IR, E, O, _R, F]
  override def input: EndpointInput[I] = endpoint.input
  override def errorOutput: EndpointOutput[E] = endpoint.errorOutput
  override def output: EndpointOutput[O] = endpoint.output
  override def info: EndpointInfo = endpoint.info

  override private[tapir] def withInfo(info: EndpointInfo): ServerEndpointInParts[U, IR, E, O, R, F] =
    new ServerEndpointInParts[U, IR, E, O, R, F] {
      override type I = outer.I
      override type T = outer.T
      override def endpoint: Endpoint[I, E, O, R] = outer.endpoint.info(info)
      override def splitInput: I => (outer.T, IR) = outer.splitInput
      override def logicFragment: MonadError[F] => T => F[Either[E, U]] = outer.logicFragment
    }

  override protected def showType: String = "FragmentedServerEndpoint"

  /** Complete the server logic for this endpoint, given the result of applying the partial server logic, and
    * the remaining input.
    */
  def andThen(remainingLogic: ((U, IR)) => F[Either[E, O]]): ServerEndpoint[R, F] = andThenM(_ => remainingLogic)

  /** Same as [[andThen]], but requires `E` to be a throwable, and coverts failed effects of type `E` to
    * endpoint errors.
    */
  def andThenRecoverErrors(
      remainingLogic: ((U, IR)) => F[O]
  )(implicit eIsThrowable: E <:< Throwable, eClassTag: ClassTag[E]): ServerEndpoint[R, F] =
    andThenM(recoverErrors(remainingLogic))

  private def andThenM(remainingLogic: MonadError[F] => ((U, IR)) => F[Either[E, O]]): ServerEndpoint[R, F] =
    ServerEndpoint(
      endpoint,
      { implicit monad => (i: I) =>
        {
          val (t, r): (T, IR) = splitInput(i)
          logicFragment(monad)(t).flatMap {
            case Left(e)  => (Left(e): Either[E, O]).unit
            case Right(u) => remainingLogic(monad)((u, r))
          }
        }
      }
    )

  /** Define logic for some part of the remaining input. The result will be an server endpoint, which will need to be
    * completed with a function accepting as arguments outputs of both previous and this server logic parts, and
    * the input.
    */
  def andThenPart[T2, R2, V, UV](
      nextPart: T2 => F[Either[E, V]]
  )(implicit
      rMinusT2: ParamSubtract.Aux[IR, T2, R2],
      uu2Concat: ParamConcat.Aux[U, V, UV]
  ): ServerEndpointInParts[UV, R2, E, O, R, F] = andThenPartM(_ => nextPart)

  /** Same as [[andThenPart]], but requires `E` to be a throwable, and coverts failed effects of type `E` to
    * endpoint errors.
    */
  def andThenPartRecoverErrors[T2, R2, V, UV](
      nextPart: T2 => F[V]
  )(implicit
      rMinusT2: ParamSubtract.Aux[IR, T2, R2],
      uu2Concat: ParamConcat.Aux[U, V, UV],
      eIsThrowable: E <:< Throwable,
      eClassTag: ClassTag[E]
  ): ServerEndpointInParts[UV, R2, E, O, R, F] = andThenPartM(recoverErrors(nextPart))

  private def andThenPartM[T2, R2, V, UV](
      part2: MonadError[F] => T2 => F[Either[E, V]]
  )(implicit
      rMinusT2: ParamSubtract.Aux[IR, T2, R2],
      uu2Concat: ParamConcat.Aux[U, V, UV]
  ): ServerEndpointInParts[UV, R2, E, O, R, F] =
    new ServerEndpointInParts[UV, R2, E, O, R, F] {
      override type I = outer.I
      override type T = (outer.T, T2)
      override def endpoint: Endpoint[outer.I, E, O, R] = outer.endpoint

      override def splitInput: I => ((outer.T, T2), R2) =
        i => {
          val (t, r) = outer.splitInput(i)
          val (t2, r2) = split(r)(rMinusT2)
          ((t, t2), r2)
        }

      override def logicFragment: MonadError[F] => T => F[Either[E, UV]] = { implicit monad =>
        { case (t, t2) =>
          outer.logicFragment(monad)(t).flatMap {
            case Left(e) => (Left(e): Either[E, UV]).unit
            case Right(u) =>
              part2(monad)(t2).map {
                case Left(e)   => Left(e): Either[E, UV]
                case Right(u2) => Right(combine(u, u2)(uu2Concat)): Either[E, UV]
              }
          }
        }
      }
    }
}
