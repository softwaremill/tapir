package sttp.tapir.server.ziohttp

import cats.effect.{IO, Resource}
import sttp.capabilities.fs2.Fs2Streams
import sttp.monad.MonadError
import sttp.tapir.server.tests.{DefaultCreateServerTest, ServerAuthenticationTests, ServerBasicTests, ServerFileMultipartTests, ServerMetricsTest, ServerStreamingTests, backendResource}
import sttp.tapir.server.zhttp.ZHttpInterpreter.zioMonadError
import sttp.tapir.tests.{Test, TestSuite}
import zio.RIO
import zio.blocking.Blocking

class ZHttpServerTest extends TestSuite {

  override def tests: Resource[IO, List[Test]] = backendResource.map { backend =>
    val interpreter = new ZHttpTestServerInterpreter()
    val createServerTest = new DefaultCreateServerTest(backend, interpreter)

    def additionalTests(): List[Test] = List()

    implicit val m: MonadError[RIO[Blocking, *]] = zioMonadError

    new ServerBasicTests(createServerTest, interpreter, false).tests()
  }
}
