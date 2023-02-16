package sttp.tapir.server.ziohttp

import cats.effect.{IO, Resource}
import org.scalatest.Assertion
import org.scalatest.matchers.should.Matchers._
import sttp.capabilities.zio.ZioStreams
import sttp.monad.MonadError
import sttp.tapir._
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}
import sttp.tapir.ztapir.{RIOMonadError, RichZEndpoint}
import zio.http.{Path, Request, URL}
import zio.http.netty.{ChannelType, EventLoopGroups, ChannelFactories}
import zio.interop.catz._
import zio.{Runtime, Promise, Ref, Task, UIO, Unsafe, ZIO, ZEnvironment, ZLayer}
import zio.http.Middleware
import java.time

class ZioHttpServerTest extends TestSuite {

  override def tests: Resource[IO, List[Test]] = backendResource.flatMap { backend =>
    implicit val r: Runtime[Any] = Runtime.default
    // creating the netty dependencies once, to speed up tests
    Resource
      .scoped[IO, Any, ZEnvironment[zio.http.service.EventLoopGroup with zio.http.service.ServerChannelFactory]]({
        val eventConfig = ZLayer.succeed(new EventLoopGroups.Config {
          def channelType = ChannelType.AUTO
          val nThreads = 0
        })

        val channelConfig: ZLayer[Any, Nothing, ChannelType.Config] = eventConfig
        (channelConfig >>> ChannelFactories.Server.fromConfig) ++ (eventConfig >>> EventLoopGroups.fromConfig)
      }.build)
      .map { nettyDeps =>
        val eventLoopGroup = ZLayer.succeed(nettyDeps.get[zio.http.service.EventLoopGroup])
        val channelFactory = ZLayer.succeed(nettyDeps.get[zio.http.service.ServerChannelFactory])
        val interpreter = new ZioHttpTestServerInterpreter(eventLoopGroup, channelFactory)
        val createServerTest = new DefaultCreateServerTest(backend, interpreter)

        def additionalTests(): List[Test] = List(
          // https://github.com/softwaremill/tapir/issues/1914
          Test("zio http route can be called with runZIO") {
            val ep = endpoint.get.in("p1").out(stringBody).zServerLogic[Any](_ => ZIO.succeed("response"))
            val route = ZioHttpInterpreter().toHttp(ep)
            val test: UIO[Assertion] = route
              .runZIO(Request.get(url = URL.apply(Path.empty / "p1")))
              .flatMap(response => response.body.asString)
              .map(_ shouldBe "response")
              .catchAll(_ => ZIO.succeed(fail("Unable to extract body from Http response")))
            Unsafe.unsafe(implicit u => r.unsafe.runToFuture(test))
          },
          Test("zio http middlewares run before the handler") {
            val test: UIO[Assertion] = for {
              p <- Promise.make[Nothing, Unit]
              ep = endpoint.get
                .in("p1")
                .out(stringBody)
                .zServerLogic[Any](_ => p.await.timeout(time.Duration.ofSeconds(1)) *> ZIO.succeed("Ok"))
              int = ZioHttpInterpreter().toHttp(ep)
              route = int @@ Middleware.allowZIO[Any, Nothing]((_: Request) => p.succeed(()).as(true))
              result <- route
                .runZIO(Request.get(url = URL(Path.empty / "p1")))
                .flatMap(response => response.body.asString)
                .map(_ shouldBe "Ok")
                .catchAll(_ => ZIO.succeed(fail("Unable to extract body from Http response")))
            } yield result

            Unsafe.unsafe(implicit u => r.unsafe.runToFuture(test))
          },
          Test("zio http middlewares only run once") {
            val test: UIO[Assertion] = for {
              ref <- Ref.make(0)
              ep = endpoint.get
                .in("p1")
                .out(stringBody)
                .zServerLogic[Any](_ => ref.updateAndGet(_ + 1).map(_.toString))
              route = ZioHttpInterpreter().toHttp(ep) @@ Middleware.allowZIO[Any, Nothing]((_: Request) => ref.update(_ + 1).as(true))
              result <- route
                .runZIO(Request.get(url = URL(Path.empty / "p1")))
                .flatMap(response => response.body.asString)
                .map(_ shouldBe "2")
                .catchAll(_ => ZIO.succeed(fail("Unable to extract body from Http response")))
            } yield result

            Unsafe.unsafe(implicit u => r.unsafe.runToFuture(test))
          }
        )

        implicit val m: MonadError[Task] = new RIOMonadError[Any]

        new ServerBasicTests(
          createServerTest,
          interpreter,
          multipleValueHeaderSupport = false,
          supportsUrlEncodedPathSegments = false,
          supportsMultipleSetCookieHeaders = false,
          invulnerableToUnsanitizedHeaders = false
        ).tests() ++
          // TODO: re-enable static content once a newer zio http is available. Currently these tests often fail with:
          // Cause: java.io.IOException: parsing HTTP/1.1 status line, receiving [f2 content], parser state [STATUS_LINE]
          new AllServerTests(
            createServerTest,
            interpreter,
            backend,
            basic = false,
            staticContent = false,
            multipart = false,
            file = false,
            reject = false,
            options = false
          ).tests() ++
          new ServerStreamingTests(createServerTest, ZioStreams).tests() ++
          new ZioHttpCompositionTest(createServerTest).tests() ++
          additionalTests()
      }
  }
}
