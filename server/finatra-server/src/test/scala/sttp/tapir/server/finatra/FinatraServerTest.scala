package sttp.tapir.server.finatra

import cats.effect.{IO, Resource}
import sttp.tapir.server.finatra.FinatraServerInterpreter.FutureMonadError
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}

class FinatraServerTest extends TestSuite {

  override def tests: Resource[IO, List[Test]] = backendResource.map { backend =>
    val interpreter = new FinatraTestServerInterpreter()
    val createServerTest = new DefaultCreateServerTest(backend, interpreter)

    new AllServerTests(createServerTest, interpreter, backend, staticContent = false, reject = false, maxContentLength = false).tests() ++
      new ServerFilesTests(interpreter, backend, supportSettingContentLength = false).tests()
  }
}
