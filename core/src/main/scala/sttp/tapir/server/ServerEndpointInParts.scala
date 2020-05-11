package sttp.tapir.server

import sttp.tapir.{Endpoint, EndpointInfo, EndpointInfoOps, EndpointInput, EndpointMetaOps, EndpointOutput}
import sttp.tapir.typelevel.{ParamConcat, ParamSubtract}
import sttp.tapir.internal._
import sttp.tapir.monad.MonadError
import sttp.tapir.monad.syntax._

/**
  * An endpoint description together with partial server logic. See [[Endpoint.serverLogicPart]].
  *
  * The part of the server logic which is provided transforms some inputs either to an error of type `E`, or value of
  * type `U`.
  *
  * The part of the server logic which is not provided, transforms a tuple: `(U, R)` either into an error of type `E`,
  * or a value of type `O`.
  *
  * @tparam U The type of the value returned by the partial server logic.
  * @tparam R Remaining input parameter types, for which logic has yet to be provided.
  * @tparam I Entire input parameter types. `I = T + R`, where `T` is the part of the input consumed by the partial
  *           logic, and converted to `U`.
  * @tparam E Error output parameter types.
  * @tparam O Output parameter types.
  * @tparam S The type of streams that are used by this endpoint's inputs/outputs. `Nothing`, if no streams are used.
  * @tparam F The effect type used in the provided server logic.
  */
abstract class ServerEndpointInParts[U, R, I, E, O, +S, F[_]](val endpoint: Endpoint[I, E, O, S])
    extends EndpointInfoOps[I, E, O, S]
    with EndpointMetaOps[I, E, O, S] { outer =>

  /**
    * Part of the input, consumed by `logicFragment`.
    */
  protected type T
  protected def splitInput: I => (T, R)
  protected def logicFragment: MonadError[F] => T => F[Either[E, U]]

  override type EndpointType[_I, _E, _O, +_S] = ServerEndpointInParts[U, R, _I, _E, _O, _S, F]
  override def input: EndpointInput[I] = endpoint.input
  override def errorOutput: EndpointOutput[E] = endpoint.errorOutput
  override def output: EndpointOutput[O] = endpoint.output
  override def info: EndpointInfo = endpoint.info

  override private[tapir] def withInfo(info: EndpointInfo): ServerEndpointInParts[U, R, I, E, O, S, F] =
    new ServerEndpointInParts[U, R, I, E, O, S, F](endpoint.info(info)) {
      override type T = outer.T
      override def splitInput: I => (outer.T, R) = outer.splitInput
      override def logicFragment: MonadError[F] => T => F[Either[E, U]] = outer.logicFragment
    }

  override protected def showType: String = "FragmentedServerEndpoint"

  /**
    * Complete the server logic for this endpoint, given the result of applying the partial server logic, and
    * the remaining input.
    */
  def andThen(remainingLogic: ((U, R)) => F[Either[E, O]]): ServerEndpoint[I, E, O, S, F] =
    ServerEndpoint(
      endpoint,
      { implicit monad => i =>
        {
          val (t, r): (T, R) = splitInput(i)
          logicFragment(monad)(t).flatMap {
            case Left(e)  => (Left(e): Either[E, O]).unit
            case Right(u) => remainingLogic((u, r))
          }
        }
      }
    )

  /**
    * Define logic for some part of the remaining input. The result will be an server endpoint, which will need to be
    * completed with a function accepting as arguments outputs of both previous and this server logic parts, and
    * the input.
    */
  def andThenPart[T2, R2, V, UV](
      part2: T2 => F[Either[E, V]]
  )(implicit
      rMinusT2: ParamSubtract.Aux[R, T2, R2],
      uu2Concat: ParamConcat.Aux[U, V, UV]
  ): ServerEndpointInParts[UV, R2, I, E, O, S, F] =
    new ServerEndpointInParts[UV, R2, I, E, O, S, F](endpoint) {
      override type T = (outer.T, T2)

      override def splitInput: I => ((outer.T, T2), R2) =
        i => {
          val (t, r) = outer.splitInput(i)
          val (t2, r2) = split(r)(rMinusT2)
          ((t, t2), r2)
        }

      override def logicFragment: MonadError[F] => T => F[Either[E, UV]] = { implicit monad =>
        {
          case (t, t2) =>
            outer.logicFragment(monad)(t).flatMap {
              case Left(e) => (Left(e): Either[E, UV]).unit
              case Right(u) =>
                part2(t2).map {
                  case Left(e)   => Left(e): Either[E, UV]
                  case Right(u2) => Right(combine(u, u2)(uu2Concat)): Either[E, UV]
                }
            }
        }
      }
    }
}
