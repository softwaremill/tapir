package sttp.tapir.server.ziohttp

import cats.effect.{IO, Resource}
import io.netty.util.CharsetUtil
import org.scalactic.source.Position.here
import org.scalatest.compatible.Assertion
import org.scalatest.matchers.should.Matchers._
import sttp.capabilities.zio.ZioStreams
import sttp.monad.MonadError
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}
import sttp.tapir.ztapir._
import zhttp.http._
import zhttp.service.server.ServerChannelFactory
import zhttp.service.{EventLoopGroup, ServerChannelFactory}
import zio.interop.catz._
import zio.{Runtime, Task, UIO, ZIO}

class ZioHttpServerTest extends TestSuite {

  override def tests: Resource[IO, List[Test]] = backendResource.flatMap { backend =>
    implicit val r: Runtime[Any] = Runtime.default
    // creating the netty dependencies once, to speed up tests
    (EventLoopGroup.auto(0) ++ ServerChannelFactory.auto).build.toResource[IO].map {
      (nettyDeps: EventLoopGroup with ServerChannelFactory) =>
        val interpreter = new ZioHttpTestServerInterpreter(nettyDeps)
        val createServerTest = new DefaultCreateServerTest(backend, interpreter)

        def additionalTests(): List[Test] = List(
          // https://github.com/softwaremill/tapir/issues/1914
          Test("zio http route can be used as a function") {
            val ep = endpoint.get.in("p1").out(stringBody).zServerLogic[Any](_ => ZIO.succeed("response"))
            val route = ZioHttpInterpreter().toHttp(ep)
            val test: UIO[Assertion] = route(Request(url = URL.apply(Path.apply("p1"))))
              .flatMap(response => response.data.toByteBuf.map(_.toString(CharsetUtil.UTF_8)))
              .map(_ shouldBe "response")
              .catchAll(_ => ZIO.succeed(fail("Unable to extract body from Http response")))
            zio.Runtime.default.unsafeRunToFuture(test)
          }
        )

        implicit val m: MonadError[Task] = new RIOMonadError[Any]

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
          new AllServerTests(createServerTest, interpreter, backend, basic = false, staticContent = false, multipart = false, file = true)
            .tests() ++
          new ServerStreamingTests(createServerTest, ZioStreams).tests() ++
          new ZioHttpCompositionTest(createServerTest).tests() // ++
        // TODO: only works with zio2
        // additionalTests()
    }
  }
}
