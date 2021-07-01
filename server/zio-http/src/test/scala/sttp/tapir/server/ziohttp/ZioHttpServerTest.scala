package sttp.tapir.server.ziohttp

import cats.effect.{IO, Resource}
import sttp.capabilities.zio.ZioStreams
import sttp.monad.MonadError
import sttp.tapir.server.tests.{
  DefaultCreateServerTest,
  ServerAuthenticationTests,
  ServerBasicTests,
  ServerMetricsTest,
  ServerStreamingTests,
  backendResource
}
import sttp.tapir.server.zhttp.ZioHttpInterpreter.zioMonadError
import sttp.tapir.tests.{Test, TestSuite}
import zio.RIO
import zio.blocking.Blocking

class ZioHttpServerTest extends TestSuite {

  override def tests: Resource[IO, List[Test]] = backendResource.map { backend =>
    val interpreter = new ZioHttpTestServerInterpreter()
    val createServerTest = new DefaultCreateServerTest(backend, interpreter)

    implicit val m: MonadError[RIO[Blocking, *]] = zioMonadError

    new ServerBasicTests(createServerTest, interpreter, false).tests() ++
      new ServerStreamingTests(createServerTest, ZioStreams).tests() ++
      new ServerAuthenticationTests(createServerTest).tests() ++
      new ServerMetricsTest(createServerTest).tests()
  }
}
