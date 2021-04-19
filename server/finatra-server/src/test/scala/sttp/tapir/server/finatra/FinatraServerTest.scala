package sttp.tapir.server.finatra

import cats.effect.{IO, Resource}
import sttp.monad.MonadError
import sttp.tapir.server.finatra.FinatraServerInterpreter.FutureMonadError
import sttp.tapir.server.tests.{CreateServerTest, ServerAuthenticationTests, ServerBasicTests, ServerMetricsTest, backendResource}
import sttp.tapir.tests.{Test, TestSuite}

class FinatraServerTest extends TestSuite {

  override def tests: Resource[IO, List[Test]] = backendResource.map { backend =>
    val interpreter = new FinatraTestServerInterpreter()
    val createServerTest = new CreateServerTest(interpreter)

    implicit val m: MonadError[com.twitter.util.Future] = FutureMonadError

    new ServerBasicTests(backend, createServerTest, interpreter).tests() ++
      new ServerAuthenticationTests(backend, createServerTest).tests() ++
      new ServerMetricsTest(backend, createServerTest).tests()
  }
}
