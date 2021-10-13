package sttp.tapir.server.finatra.cats

import cats.effect.{IO, Resource}
import sttp.client3.impl.cats.CatsMonadAsyncError
import sttp.tapir.server.tests.{DefaultCreateServerTest, ServerAuthenticationTests, ServerBasicTests, ServerMultipartTests, backendResource}
import sttp.tapir.tests.{Test, TestSuite}

class FinatraServerCatsTests extends TestSuite {
  override def tests: Resource[IO, List[Test]] = backendResource.map { backend =>
    implicit val m: CatsMonadAsyncError[IO] = new CatsMonadAsyncError[IO]()

    val interpreter = new FinatraCatsTestServerInterpreter(dispatcher)
    val createTestServer = new DefaultCreateServerTest(backend, interpreter)

    new ServerBasicTests(createTestServer, interpreter).tests() ++
      new ServerMultipartTests(createTestServer).tests() ++
      new ServerAuthenticationTests(createTestServer).tests()
  }
}
