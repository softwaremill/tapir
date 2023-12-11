package sttp.tapir.server.nima

import cats.effect.{IO, Resource}
import org.scalatest.EitherValues
import sttp.tapir.server.nima.internal.idMonad
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}

class NimaServerTest extends TestSuite with EitherValues {
  override def tests: Resource[IO, List[Test]] =
    backendResource.flatMap { backend =>
      Resource
        .eval(IO.delay {
          val interpreter = new NimaTestServerInterpreter()
          val createServerTest = new DefaultCreateServerTest(backend, interpreter)
          // TODO uncomment static content tests when Nima starts to correctly support '*' in accept-encoding
          new ServerBasicTests(createServerTest, interpreter, invulnerableToUnsanitizedHeaders = false, maxContentLength = false).tests() ++
            new AllServerTests(createServerTest, interpreter, backend, basic = false, multipart = false, staticContent = false).tests()
        })
    }
}
