package sttp.tapir.server.ziohttp

import cats.effect.{IO, Resource}
import sttp.capabilities.zio.ZioStreams
import sttp.monad.MonadError
import sttp.tapir.server.tests._
import sttp.tapir.server.ziohttp.ZioHttpInterpreter.zioMonadError
import sttp.tapir.tests.{Test, TestSuite}
import zhttp.service.server.ServerChannelFactory
import zhttp.service.{EventLoopGroup, ServerChannelFactory}
import zio.interop.catz._
import zio.{Runtime, Task, ZEnvironment}

class ZioHttpServerTest extends TestSuite {

  override def tests: Resource[IO, List[Test]] = backendResource.flatMap { backend =>
    implicit val r: Runtime[Any] = Runtime.default
    // creating the netty dependencies once, to speed up tests
    (EventLoopGroup.auto(0) ++ ServerChannelFactory.auto).build.toResource[IO].map {
      (nettyDeps: ZEnvironment[EventLoopGroup with ServerChannelFactory]) =>
        val interpreter = new ZioHttpTestServerInterpreter(nettyDeps)
        val createServerTest = new DefaultCreateServerTest(backend, interpreter)

        implicit val m: MonadError[Task] = zioMonadError

        new ServerBasicTests(
          createServerTest,
          interpreter,
          multipleValueHeaderSupport = false,
          inputStreamSupport = true,
          supportsUrlEncodedPathSegments = false,
          supportsMultipleSetCookieHeaders = false,
          invulnerableToUnsanitizedHeaders = false
        ).tests() ++
          // TODO: re-enable static content once a newer zio http is available. Currently these tests often fail with:
          // Cause: java.io.IOException: parsing HTTP/1.1 status line, receiving [f2 content], parser state [STATUS_LINE]
          new AllServerTests(createServerTest, interpreter, backend, basic = false, staticContent = false, multipart = false, file = false)
            .tests() ++
          new ServerStreamingTests(createServerTest, ZioStreams).tests() ++
          new ZioHttpCompositionTest(createServerTest).tests()
    }
  }
}
