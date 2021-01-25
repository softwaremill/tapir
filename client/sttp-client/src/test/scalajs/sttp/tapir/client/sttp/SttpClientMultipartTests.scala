package sttp.tapir.client.sttp

import sttp.tapir.client.tests.ClientMultipartTests

import scala.concurrent.Future

class SttpClientMultipartTests[F[_]] extends SttpClientTests[Any] with ClientMultipartTests[Future] {
  override def wsToPipe: WebSocketToPipe[Any] = implicitly

  multipartTests()
}
