package sttp.tapir.client.sttp

import sttp.tapir.client.tests.ClientBasicTests

class SttpClientBasicZioTests extends SttpClientZioTests[Any] with ClientBasicTests {
  override def wsToPipe: WebSocketToPipe[Any] = implicitly

  tests()
}
