package sttp.tapir.client.sttp4

import sttp.tapir.client.sttp4.WebSocketToPipe
import sttp.tapir.client.tests.ClientMultipartTests

class SttpClientMultipartTests extends SttpClientTests[Any] with ClientMultipartTests {
  override def wsToPipe: WebSocketToPipe[Any] = implicitly

  multipartTests()
}
