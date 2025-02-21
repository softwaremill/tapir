package sttp.tapir.client.sttp4

import sttp.tapir.client.sttp4.WebSocketToPipe
import sttp.tapir.client.tests.ClientBasicTests

class SttpClientBasicTests extends SttpClientTests[Any] with ClientBasicTests {
  override def wsToPipe: WebSocketToPipe[Any] = implicitly

  tests()
}
