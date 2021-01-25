package sttp.tapir.client.sttp

import cats.effect.IO
import sttp.tapir.client.tests.ClientMultipartTests

class SttpClientMultipartTests[F[_]] extends SttpClientTests[Any] with ClientMultipartTests[IO] {
  override def wsToPipe: WebSocketToPipe[Any] = implicitly

  multipartTests()
}
