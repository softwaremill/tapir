package sttp.tapir.server.jdkhttp

import cats.effect.{IO, Resource}
import org.scalatest.EitherValues
import sttp.tapir.server.jdkhttp.internal.idMonad
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}

class JdkHttpServerTest extends TestSuite with EitherValues {
  // these tests often fail on CI with:
  // "Cause: java.io.IOException: HTTP/1.1 header parser received no bytes"
  // "Cause: java.io.EOFException: EOF reached while reading"
  // for an unknown reason; adding retries to avoid flaky tests
  override def retries = 5

  override def tests: Resource[IO, List[Test]] =
    backendResource.flatMap { backend =>
      Resource
        .eval(
          IO.delay {
            val interpreter = new JdkHttpTestServerInterpreter()
            val createServerTest = new DefaultCreateServerTest(backend, interpreter)

            new ServerBasicTests(createServerTest, interpreter, invulnerableToUnsanitizedHeaders = false).tests() ++
              new AllServerTests(createServerTest, interpreter, backend, basic = false, multipart = false, metrics = false).tests() ++
              new ServerMultipartTests(createServerTest, utf8FileNameSupport = false).tests()
          }
        )
    }
}
