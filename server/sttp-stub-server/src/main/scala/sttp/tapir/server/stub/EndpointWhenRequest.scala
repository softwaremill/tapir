package sttp.tapir.server.stub

import sttp.client3.testing.SttpBackendStub
import sttp.tapir.Endpoint

case class EndpointWhenRequest[F[_], R, A, I, E, O](private val endpoint: Endpoint[A, I, E, O, R], private val stub: SttpBackendStub[F, R]) {

  def thenSuccess(response: O)

}
