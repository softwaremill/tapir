package sttp.tapir.server.finatra.cats

import cats.effect.{IO, Resource}
import sttp.client3.impl.cats.CatsMonadAsyncError
import sttp.tapir.server.tests.{ServerAuthenticationTests, ServerBasicTests, ServerTests, backendResource}
import sttp.tapir.tests.{Test, TestSuite}

class FinatraServerCatsTests extends TestSuite {
  override def tests: Resource[IO, List[Test]] = backendResource.map { backend =>
    implicit val m: CatsMonadAsyncError[IO] = new CatsMonadAsyncError[IO]()
    val interpreter = new FinatraServerCatsInterpreter()
    val serverTests = new ServerTests(interpreter)

    new ServerBasicTests(backend, serverTests, interpreter).tests() ++ new ServerAuthenticationTests(backend, serverTests).tests()
  }
}
