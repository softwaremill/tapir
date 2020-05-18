package sttp.tapir.server

import sttp.tapir.{Endpoint, EndpointInfo, EndpointInfoOps, EndpointInput, EndpointMetaOps, EndpointOutput}
import sttp.tapir.monad.MonadError

/**
  * @tparam I Input parameter types.
  * @tparam E Error output parameter types.
  * @tparam O Output parameter types.
  * @tparam S The type of streams that are used by this endpoint's inputs/outputs. `Nothing`, if no streams are used.
  * @tparam F The effect type used in the provided server logic.
  */
case class ServerEndpoint[I, E, O, +S, F[_]](endpoint: Endpoint[I, E, O, S], logic: MonadError[F] => I => F[Either[E, O]])
    extends EndpointInfoOps[I, E, O, S]
    with EndpointMetaOps[I, E, O, S] {

  override type EndpointType[_I, _E, _O, +_S] = ServerEndpoint[_I, _E, _O, _S, F]
  override def input: EndpointInput[I] = endpoint.input
  override def errorOutput: EndpointOutput[E] = endpoint.errorOutput
  override def output: EndpointOutput[O] = endpoint.output
  override def info: EndpointInfo = endpoint.info
  override private[tapir] def withInfo(info: EndpointInfo): ServerEndpoint[I, E, O, S, F] = copy(endpoint = endpoint.info(info))

  override protected def showType: String = "ServerEndpoint"
}
