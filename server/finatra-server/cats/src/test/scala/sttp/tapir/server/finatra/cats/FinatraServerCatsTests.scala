package sttp.tapir.server.finatra.cats

import cats.effect.{IO, Resource}
import sttp.client3.impl.cats.CatsMonadAsyncError
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}

class FinatraServerCatsTests extends TestSuite {
  override def tests: Resource[IO, List[Test]] = backendResource.map { backend =>
    implicit val m: CatsMonadAsyncError[IO] = new CatsMonadAsyncError[IO]()

    val interpreter = new FinatraCatsTestServerInterpreter(dispatcher)
    val createServerTest = new DefaultCreateServerTest(backend, interpreter)

    new AllServerTests(
      createServerTest,
      interpreter,
      backend,
      staticContent = false,
      reject = false,
      metrics = false,
      maxContentLength = false
    ).tests() ++
      new ServerFilesTests(interpreter, backend, supportSettingContentLength = false).tests()
  }
}
