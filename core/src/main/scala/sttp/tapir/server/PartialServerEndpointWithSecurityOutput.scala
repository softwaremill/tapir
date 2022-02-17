package sttp.tapir.server

import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir._
import sttp.tapir.internal._

import scala.reflect.ClassTag

case class PartialServerEndpointWithSecurityOutput[A, U, I, E, SECURITY_OUTPUT, O, -R, F[_]](
    securityOutput: EndpointOutput[SECURITY_OUTPUT],
    endpoint: Endpoint[A, I, E, O, R],
    securityLogic: MonadError[F] => A => F[Either[E, (SECURITY_OUTPUT, U)]]
) extends EndpointInputsOps[A, I, E, O, R]
    with EndpointOutputsOps[A, I, E, O, R]
    with EndpointErrorOutputVariantsOps[A, I, E, O, R]
    with EndpointInfoOps[R]
    with EndpointMetaOps { outer =>
  override type ThisType[-_R] = PartialServerEndpointWithSecurityOutput[A, U, I, E, SECURITY_OUTPUT, O, _R, F]
  override type EndpointType[_A, _I, _E, _O, -_R] = PartialServerEndpointWithSecurityOutput[_A, U, _I, _E, SECURITY_OUTPUT, _O, _R, F]

  override def securityInput: EndpointInput[A] = endpoint.securityInput
  override def input: EndpointInput[I] = endpoint.input
  override def errorOutput: EndpointOutput[E] = endpoint.errorOutput
  override def output: EndpointOutput[O] = endpoint.output
  override def info: EndpointInfo = endpoint.info

  override private[tapir] def withInput[I2, R2](
      input: EndpointInput[I2]
  ): PartialServerEndpointWithSecurityOutput[A, U, I2, E, SECURITY_OUTPUT, O, R with R2, F] =
    copy(endpoint = endpoint.copy(input = input))
  override private[tapir] def withOutput[O2, R2](output: EndpointOutput[O2]) = copy(endpoint = endpoint.copy(output = output))
  override private[tapir] def withErrorOutputVariant[E2, R2](
      errorOutput: EndpointOutput[E2],
      embedE: E => E2
  ): PartialServerEndpointWithSecurityOutput[A, U, I, E2, SECURITY_OUTPUT, O, R with R2, F] =
    this.copy(
      endpoint = endpoint.copy(errorOutput = errorOutput),
      securityLogic = implicit m =>
        a =>
          securityLogic(m)(a).map {
            case Left(e)  => Left(embedE(e))
            case Right(o) => Right(o)
          }
    )
  override private[tapir] def withInfo(info: EndpointInfo) = copy(endpoint = endpoint.copy(info = info))

  override protected def showType: String = "PartialServerEndpointWithSecurityOutput"

  def serverLogic(f: U => I => F[Either[E, O]]): ServerEndpoint.Full[A, (SECURITY_OUTPUT, U), I, E, (SECURITY_OUTPUT, O), R, F] =
    ServerEndpoint(
      endpoint.prependOut(securityOutput),
      securityLogic,
      implicit m => so_u => i => f(so_u._2)(i).map(_.right.map(o => (so_u._1, o)))
    )

  def serverLogicSuccess(f: U => I => F[O]): ServerEndpoint.Full[A, (SECURITY_OUTPUT, U), I, E, (SECURITY_OUTPUT, O), R, F] =
    ServerEndpoint(
      endpoint.prependOut(securityOutput),
      securityLogic,
      implicit m => so_u => i => f(so_u._2)(i).map(o => Right((so_u._1, o)))
    )

  def serverLogicError(f: U => I => F[E]): ServerEndpoint.Full[A, (SECURITY_OUTPUT, U), I, E, (SECURITY_OUTPUT, O), R, F] =
    ServerEndpoint(endpoint.prependOut(securityOutput), securityLogic, implicit m => so_u => i => f(so_u._2)(i).map(Left(_)))

  def serverLogicPure(f: U => I => Either[E, O]): ServerEndpoint.Full[A, (SECURITY_OUTPUT, U), I, E, (SECURITY_OUTPUT, O), R, F] =
    ServerEndpoint(
      endpoint.prependOut(securityOutput),
      securityLogic,
      implicit m => so_u => i => f(so_u._2)(i).right.map(o => (so_u._1, o)).unit
    )

  def serverLogicRecoverErrors(
      f: U => I => F[O]
  )(implicit
      eIsThrowable: E <:< Throwable,
      eClassTag: ClassTag[E]
  ): ServerEndpoint.Full[A, (SECURITY_OUTPUT, U), I, E, (SECURITY_OUTPUT, O), R, F] =
    ServerEndpoint(
      endpoint.prependOut(securityOutput),
      securityLogic,
      implicit m =>
        recoverErrors2[(SECURITY_OUTPUT, U), I, E, (SECURITY_OUTPUT, O), F](so_u => i => f(so_u._2)(i).map(o => (so_u._1, o)))(
          implicitly,
          implicitly
        )(m)
    )

  def serverLogicOption(f: U => I => F[Option[O]])(implicit
      eIsUnit: E =:= Unit
  ): ServerEndpoint.Full[A, (SECURITY_OUTPUT, U), I, Unit, (SECURITY_OUTPUT, O), R, F] =
    ServerEndpoint(
      endpoint.prependOut(securityOutput).asInstanceOf[Endpoint[A, I, Unit, (SECURITY_OUTPUT, O), R]],
      securityLogic.asInstanceOf[MonadError[F] => A => F[Either[Unit, (SECURITY_OUTPUT, U)]]],
      implicit m =>
        so_u =>
          i =>
            f(so_u._2)(i).map {
              case None    => Left(())
              case Some(v) => Right((so_u._1, v))
            }
    )
}
