package sttp.tapir.client.sttp

import sttp.tapir.client.tests.ClientBasicTests
import sttp.tapir.tests.PortCounter

class SttpClientBasicTests extends SttpClientTests[Any] with ClientBasicTests {
  override def wsToPipe: WebSocketToPipe[Any] = implicitly

  basicTests()

  override val portCounter: PortCounter = new PortCounter(36000)
}
