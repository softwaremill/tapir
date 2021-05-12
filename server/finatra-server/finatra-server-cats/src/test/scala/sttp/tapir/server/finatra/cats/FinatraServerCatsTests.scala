package sttp.tapir.server.finatra.cats

import cats.effect.{IO, Resource}
import sttp.client3.impl.cats.CatsMonadAsyncError
import sttp.tapir.server.tests.{CreateTestServer, ServerAuthenticationTests, ServerBasicTests, backendResource}
import sttp.tapir.tests.{Test, TestSuite}

class FinatraServerCatsTests extends TestSuite {
  override def tests: Resource[IO, List[Test]] = backendResource.map { backend =>
    implicit val m: CatsMonadAsyncError[IO] = new CatsMonadAsyncError[IO]()

    val interpreter = new FinatraCatsTestServerInterpreter()
    val createServerTest = new CreateTestServer(interpreter)

    new ServerBasicTests(backend, createServerTest, interpreter).tests() ++
      new ServerAuthenticationTests(backend, createServerTest).tests()
  }
}
