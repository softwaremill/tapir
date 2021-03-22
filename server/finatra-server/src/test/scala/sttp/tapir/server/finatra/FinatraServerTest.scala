package sttp.tapir.server.finatra

import cats.effect.{IO, Resource}
import sttp.tapir.server.finatra.FinatraServerInterpreter.FutureMonadError
import sttp.tapir.server.tests.{ServerAuthenticationTests, ServerBasicTests, CreateServerTest, backendResource}
import sttp.tapir.tests.{Test, TestSuite}

class FinatraServerTest extends TestSuite {

  override def tests: Resource[IO, List[Test]] = backendResource.map { backend =>
    val interpreter = new FinatraTestServerInterpreter()
    val createServerTest = new CreateServerTest(interpreter)

    new ServerBasicTests(backend, createServerTest, interpreter).tests() ++ new ServerAuthenticationTests(backend, createServerTest).tests()
  }
}
