package sttp.tapir.client.sttp

import sttp.tapir.client.tests.ClientBasicTests

import scala.concurrent.Future

class SttpClientBasicTests extends SttpClientTests[Any] with ClientBasicTests[Future] {
  override def wsToPipe: WebSocketToPipe[Any] = implicitly

  basicTests()
}
