package sttp.tapir.server.jdkhttp

import cats.effect.{IO, Resource}
import org.scalatest.EitherValues
import sttp.tapir.server.jdkhttp.internal.idMonad
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}

class JdkHttpServerTest extends TestSuite with EitherValues {
  override def tests: Resource[IO, List[Test]] =
    backendResource.flatMap { backend =>
      Resource
        .eval(IO.delay {
          val interpreter = new JdkHttpTestServerInterpreter()
          val createServerTest = new DefaultCreateServerTest(backend, interpreter)

          new ServerBasicTests(createServerTest, interpreter, invulnerableToUnsanitizedHeaders = false).tests() ++
            new ServerMultipartTests(createServerTest, chunkingSupport = false)
              .tests() ++ // chunking disabled, backend rejects content-length with transfer-encoding
            new AllServerTests(createServerTest, interpreter, backend, basic = false, multipart = false).tests()
        })
    }
}
