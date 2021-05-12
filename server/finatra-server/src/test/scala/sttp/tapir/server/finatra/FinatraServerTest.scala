package sttp.tapir.server.finatra

import cats.effect.{IO, Resource}
import sttp.monad.MonadError
import sttp.tapir.server.finatra.FinatraServerInterpreter.FutureMonadError
import sttp.tapir.server.tests.{CreateTestServer, ServerAuthenticationTests, ServerBasicTests, ServerMetricsTest, backendResource}
import sttp.tapir.tests.{Test, TestSuite}

class FinatraServerTest extends TestSuite {

  override def tests: Resource[IO, List[Test]] = backendResource.map { backend =>
    val interpreter = new FinatraTestServerInterpreter()
    val createTestServer = new CreateTestServer(backend, interpreter)

    implicit val m: MonadError[com.twitter.util.Future] = FutureMonadError

    new ServerBasicTests(createTestServer, interpreter).tests() ++
      new ServerAuthenticationTests(createTestServer).tests() ++
      new ServerMetricsTest(createTestServer).tests()
  }
}
