package sttp.tapir.server

import sttp.monad.MonadError
import sttp.tapir.{Endpoint, EndpointInfo, EndpointInfoOps, EndpointInput, EndpointMetaOps, EndpointOutput}

/** @tparam I Input parameter types.
  * @tparam E Error output parameter types.
  * @tparam O Output parameter types.
  * @tparam R The capabilities that are required by this endpoint's inputs/outputs. `Any`, if no requirements.
  * @tparam F The effect type used in the provided server logic.
  */
case class ServerEndpoint[I, E, O, -R, F[_]](endpoint: Endpoint[I, E, O, R], logic: MonadError[F] => I => F[Either[E, O]])
    extends EndpointInfoOps[I, E, O, R]
    with EndpointMetaOps[I, E, O, R] {

  override type EndpointType[_I, _E, _O, -_R] = ServerEndpoint[_I, _E, _O, _R, F]
  override def input: EndpointInput[I] = endpoint.input
  override def errorOutput: EndpointOutput[E] = endpoint.errorOutput
  override def output: EndpointOutput[O] = endpoint.output
  override def info: EndpointInfo = endpoint.info
  override private[tapir] def withInfo(info: EndpointInfo): ServerEndpoint[I, E, O, R, F] = copy(endpoint = endpoint.info(info))

  override protected def showType: String = "ServerEndpoint"
}
