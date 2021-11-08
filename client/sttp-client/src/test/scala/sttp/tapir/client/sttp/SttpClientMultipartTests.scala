package sttp.tapir.client.sttp

import sttp.tapir.client.tests.ClientMultipartTests

class SttpClientMultipartTests extends SttpClientFs2Tests[Any] with ClientMultipartTests {
  override def wsToPipe: WebSocketToPipe[Any] = implicitly

  multipartTests()
}
