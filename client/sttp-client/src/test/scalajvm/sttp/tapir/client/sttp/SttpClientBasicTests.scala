package sttp.tapir.client.sttp

import cats.effect.IO
import sttp.tapir.client.tests.ClientBasicTests

class SttpClientBasicTests extends SttpClientTests[Any] with ClientBasicTests[IO] {
  override def wsToPipe: WebSocketToPipe[Any] = implicitly

  basicTests()
}
