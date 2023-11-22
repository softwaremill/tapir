package sttp.tapir.server.armeria

import cats.effect.{IO, Resource}
import sttp.capabilities.armeria.ArmeriaStreams
import sttp.monad.FutureMonad
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}
import scala.concurrent.Future

class ArmeriaFutureServerTest extends TestSuite {

  override def tests: Resource[IO, List[Test]] = backendResource.map { backend =>
    implicit val m: FutureMonad = new FutureMonad()

    val interpreter = new ArmeriaTestFutureServerInterpreter()
    val createServerTest = new DefaultCreateServerTest(backend, interpreter)

    new AllServerTests(createServerTest, interpreter, backend, basic = false, options = false).tests() ++
      new ServerBasicTests(createServerTest, interpreter, supportsUrlEncodedPathSegments = false).tests() ++
      new ServerStreamingTests(createServerTest, maxLengthSupported = false).tests(ArmeriaStreams)(_ => Future.unit)
  }
}
