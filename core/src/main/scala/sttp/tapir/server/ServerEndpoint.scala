package sttp.tapir.server

import sttp.monad.MonadError
import sttp.tapir.{Endpoint, EndpointInfo, EndpointInfoOps, EndpointInput, EndpointMetaOps, EndpointOutput}

/** An [[Endpoint]] together with a matching function, implementing the endpoint's logic.
  * @tparam R The capabilities that are required by this endpoint's inputs/outputs. `Any`, if no requirements.
  * @tparam F The effect type used in the provided server logic.
  */
abstract class ServerEndpoint[-R, F[_]]() extends EndpointInfoOps[R] with EndpointMetaOps {
  type I
  type E
  type O

  def endpoint: Endpoint[I, E, O, R]
  def logic: MonadError[F] => I => F[Either[E, O]]

  override type ThisType[-_R] = ServerEndpoint[_R, F]
  override def input: EndpointInput[I] = endpoint.input
  override def errorOutput: EndpointOutput[E] = endpoint.errorOutput
  override def output: EndpointOutput[O] = endpoint.output
  override def info: EndpointInfo = endpoint.info
  override private[tapir] def withInfo(info: EndpointInfo): ThisType[R] = ServerEndpoint(endpoint.info(info), logic)

  override protected def showType: String = "ServerEndpoint"
}

object ServerEndpoint {

  /** Create a server endpoint, where the server `logic` must match the shape of the `endpoint`. */
  def apply[I, E, O, R, F[_]](
      endpoint: Endpoint[I, E, O, R],
      logic: MonadError[F] => I => F[Either[E, O]]
  ): ServerEndpoint[R, F] = {
    type _I = I
    type _E = E
    type _O = O
    val e = endpoint
    val l = logic
    new ServerEndpoint[R, F] {
      override type I = _I
      override type E = _E
      override type O = _O
      override def endpoint: Endpoint[I, E, O, R] = e
      override def logic: MonadError[F] => I => F[Either[E, O]] = l
    }
  }
}
