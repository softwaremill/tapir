package sttp.tapir.server.finatra

import cats.effect.{IO, Resource}
import com.twitter.util.Future
import sttp.monad.MonadError
import sttp.tapir.server.tests.{ServerAuthenticationTests, ServerBasicTests, ServerEffectfulMappingsTests, ServerTests, backendResource}
import sttp.tapir.tests.{Test, TestSuite}

class FinatraServerTests extends TestSuite {

  override def tests: Resource[IO, List[Test]] = backendResource.map { backend =>
    implicit val m: MonadError[Future] = FutureMonadError
    val interpreter = new FinatraTestServerInterpreter()
    val serverTests = new ServerTests(interpreter)

    new ServerBasicTests(backend, serverTests, interpreter).tests() ++ new ServerAuthenticationTests(backend, serverTests)
      .tests() ++ new ServerEffectfulMappingsTests(backend, serverTests).tests()
  }
}
