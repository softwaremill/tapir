package sttp.tapir.server.finatra

import cats.effect.{IO, Resource}
import sttp.tapir.server.finatra.FinatraServerInterpreter.FutureMonadError
import sttp.tapir.server.tests.{ServerAuthenticationTests, ServerBasicTests, ServerTests, backendResource}
import sttp.tapir.tests.{Test, TestSuite}

class FinatraServerTests extends TestSuite {

  override def tests: Resource[IO, List[Test]] = backendResource.map { backend =>
    implicit val m = FutureMonadError
    val interpreter = new FinatraTestServerInterpreter()
    val serverTests = new ServerTests(interpreter)

    new ServerBasicTests(backend, serverTests, interpreter).tests() ++ new ServerAuthenticationTests(backend, serverTests).tests()
  }
}
