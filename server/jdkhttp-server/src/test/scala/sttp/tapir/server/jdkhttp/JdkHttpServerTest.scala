package sttp.tapir.server.jdkhttp

import cats.effect.{IO, Resource}
import org.scalatest.{EitherValues, Exceptional, FutureOutcome}
import sttp.tapir.server.jdkhttp.internal.idMonad
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}

import scala.concurrent.Future

class JdkHttpServerTest extends TestSuite with EitherValues {
  // these tests often fail on CI with:
  // "Cause: java.io.IOException: HTTP/1.1 header parser received no bytes"
  // "Cause: java.io.EOFException: EOF reached while reading"
  // for an unknown reason; adding retries to avoid flaky tests
  val retries = 5

  override def withFixture(test: NoArgAsyncTest): FutureOutcome = withFixture(test, retries)

  def withFixture(test: NoArgAsyncTest, count: Int): FutureOutcome = {
    val outcome = super.withFixture(test)
    new FutureOutcome(outcome.toFuture.flatMap {
      case Exceptional(e) =>
        println(s"Test ${test.name} failed, retrying.")
        e.printStackTrace()
        (if (count == 1) super.withFixture(test) else withFixture(test, count - 1)).toFuture
      case other => Future.successful(other)
    })
  }

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
