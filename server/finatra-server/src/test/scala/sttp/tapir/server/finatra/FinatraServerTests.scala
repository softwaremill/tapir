package sttp.tapir.server.finatra

import cats.effect.{IO, Resource}
import sttp.tapir.server.finatra
import sttp.tapir.server.tests.{ServerAuthenticationTests, ServerBasicTests, ServerTests, backendResource}
import sttp.tapir.tests.{Test, TestSuite}

class FinatraServerTests extends TestSuite {

  override def tests: Resource[IO, List[Test]] = backendResource.map { backend =>
    implicit val m: finatra.FutureMonadError.type = FutureMonadError
    val interpreter = new FinatraServerInterpreter()
    val serverTests = new ServerTests(interpreter)

    new ServerBasicTests(backend, serverTests, interpreter).tests() ++ new ServerAuthenticationTests(backend, serverTests).tests()
  }
}
