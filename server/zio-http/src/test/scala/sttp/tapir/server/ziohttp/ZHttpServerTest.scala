package sttp.tapir.server.ziohttp

import cats.effect.{IO, Resource}
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.server.tests.{
  DefaultCreateServerTest,
  ServerAuthenticationTests,
  ServerBasicTests,
  ServerFileMultipartTests,
  ServerMetricsTest,
  ServerStreamingTests,
  backendResource
}
import sttp.tapir.tests.{Test, TestSuite}

class ZHttpServerTest extends TestSuite {

  override def tests: Resource[IO, List[Test]] = backendResource.flatMap { backend =>
    val interpreter = new ZHttpTestServerInterpreter[zio.ZEnv]()
    val createServerTest = new DefaultCreateServerTest(backend, interpreter)

    def additionalTests(): List[Test] = List()

    new ServerBasicTests(createServerTest, interpreter).tests() ++
      new ServerFileMultipartTests(createServerTest).tests() ++
      new ServerStreamingTests(createServerTest, Fs2Streams[IO]).tests() ++
      new ServerAuthenticationTests(createServerTest).tests() ++
      new ServerMetricsTest(createServerTest).tests() ++
      additionalTests()
  }
}
