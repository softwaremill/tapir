package sttp.tapir.client.sttp

import sttp.tapir.client.tests.ClientBasicTests

class SttpClientBasicTests extends SttpClientFs2Tests[Any] with ClientBasicTests {
  override def wsToPipe: WebSocketToPipe[Any] = implicitly

  tests()
}
