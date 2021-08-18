package sttp.tapir.server.finatra

import cats.effect.{IO, Resource}
import sttp.tapir.server.finatra.FinatraServerInterpreter.FutureMonadError
import sttp.tapir.server.tests.{
  DefaultCreateServerTest,
  ServerAuthenticationTests,
  ServerBasicTests,
  ServerFileMultipartTests,
  ServerMetricsTest,
  backendResource
}
import sttp.tapir.tests.{Test, TestSuite}

class FinatraServerTest extends TestSuite {

  override def tests: Resource[IO, List[Test]] = backendResource.map { backend =>
    val interpreter = new FinatraTestServerInterpreter()
    val createServerTest = new DefaultCreateServerTest(backend, interpreter)

    new ServerBasicTests(createServerTest, interpreter).tests() ++
      new ServerFileMultipartTests(createServerTest).tests() ++
      new ServerAuthenticationTests(createServerTest).tests() ++
      new ServerMetricsTest(createServerTest).tests()
  }
}
