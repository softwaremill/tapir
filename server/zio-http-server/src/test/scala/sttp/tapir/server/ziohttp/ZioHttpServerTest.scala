package sttp.tapir.server.ziohttp

import cats.effect.{IO, Resource}
import io.netty.channel.{ChannelFactory, EventLoopGroup, ServerChannel}
import org.scalatest.{Assertion, Exceptional, FutureOutcome}
import org.scalatest.matchers.should.Matchers._
import sttp.capabilities.zio.ZioStreams
import sttp.client3._
import sttp.client3.testing.SttpBackendStub
import sttp.model.MediaType
import sttp.monad.MonadError
import sttp.tapir.{PublicEndpoint, _}
import sttp.tapir.server.stub.TapirStubInterpreter
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}
import sttp.tapir.ztapir.{RIOMonadError, RichZEndpoint}
import zio.{Promise, Ref, Runtime, Task, UIO, Unsafe, ZEnvironment, ZIO, ZLayer}
import zio.http.{HttpAppMiddleware, Path, Request, URL}
import zio.http.netty.{ChannelFactories, ChannelType, EventLoopGroups}
import zio.interop.catz._
import zio.stream.{ZPipeline, ZStream}

import java.nio.charset.Charset
import java.time
import scala.concurrent.Future

class ZioHttpServerTest extends TestSuite {

  // zio-http tests often fail with "Cause: java.io.IOException: parsing HTTP/1.1 status line, receiving [DEFAULT], parser state [STATUS_LINE]"
  // until this is fixed, adding retries to avoid flaky tests
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

  override def tests: Resource[IO, List[Test]] = backendResource.flatMap { backend =>
    implicit val r: Runtime[Any] = Runtime.default
    // creating the netty dependencies once, to speed up tests
    Resource
      .scoped[IO, Any, ZEnvironment[EventLoopGroup with ChannelFactory[ServerChannel]]]({
        val eventConfig = ZLayer.succeed(new EventLoopGroups.Config {
          def channelType = ChannelType.AUTO
          val nThreads = 0
        })

        val channelConfig: ZLayer[Any, Nothing, ChannelType.Config] = eventConfig
        (channelConfig >>> ChannelFactories.Server.fromConfig) ++ (eventConfig >>> EventLoopGroups.live)
      }.build)
      .map { nettyDeps =>
        val eventLoopGroup = ZLayer.succeed(nettyDeps.get[EventLoopGroup])
        val channelFactory = ZLayer.succeed(nettyDeps.get[ChannelFactory[ServerChannel]])
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
              route = int @@ HttpAppMiddleware.allowZIO((_: Request) => p.succeed(()).as(true))
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
              route = ZioHttpInterpreter()
                .toHttp(ep) @@ HttpAppMiddleware.allowZIO((_: Request) => ref.update(_ + 1).as(true))
              result <- route
                .runZIO(Request.get(url = URL(Path.empty / "p1")))
                .flatMap(response => response.body.asString)
                .map(_ shouldBe "2")
                .catchAll(_ => ZIO.succeed(fail("Unable to extract body from Http response")))
            } yield result

            Unsafe.unsafe(implicit u => r.unsafe.runToFuture(test))
          },
          // https://github.com/softwaremill/tapir/issues/2849
          Test("Streaming works through the stub backend") {
            // given
            val CsvCodecFormat = new CodecFormat {
              override def mediaType: MediaType = MediaType.TextCsv
            }

            val backendStub: TapirStubInterpreter[Task, ZioStreams, Unit] =
              TapirStubInterpreter[Task, ZioStreams](SttpBackendStub[Task, ZioStreams](new RIOMonadError[Any]))

            val endpointModel: PublicEndpoint[ZStream[Any, Throwable, Byte], Unit, ZStream[Any, Throwable, Byte], ZioStreams] =
              endpoint.post
                .in("hello")
                .in(streamBinaryBody(ZioStreams)(CsvCodecFormat))
                .out(streamBinaryBody(ZioStreams)(CsvCodecFormat))

            val streamingEndpoint: sttp.tapir.ztapir.ZServerEndpoint[Any, ZioStreams] =
              endpointModel
                .zServerLogic(stream =>
                  ZIO.succeed {
                    stream
                      .via(ZPipeline.utf8Decode)
                      .via(ZPipeline.splitLines)
                      .via(ZPipeline.intersperse(java.lang.System.lineSeparator()))
                      .via(ZPipeline.utf8Encode)
                  }
                )
            val inputStrings = List("Hello,how,are,you", "I,am,good,thanks")
            val input: ZStream[Any, Nothing, Byte] =
              ZStream(inputStrings: _*)
                .via(ZPipeline.intersperse(java.lang.System.lineSeparator()))
                .mapConcat(_.getBytes(Charset.forName("UTF-8")))

            val makeRequest = sttp.client3.basicRequest
              .streamBody(ZioStreams)(input)
              .post(uri"/hello")
              .response(asStreamAlwaysUnsafe(ZioStreams))

            val backend = backendStub.whenServerEndpointRunLogic(streamingEndpoint).backend()

            // when
            val test: ZIO[Any, Throwable, Assertion] =
              makeRequest
                .send(backend)
                .flatMap(response =>
                  response.body
                    .via(ZPipeline.utf8Decode)
                    .via(ZPipeline.splitLines)
                    .runCollect
                    .map(_.toList)
                )
                // then
                .map(_ shouldBe inputStrings)
                .catchAll { _ =>
                  ZIO.succeed(fail("Unable to extract body from Http response"))
                }

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
            options = false
          ).tests() ++
          new ServerStreamingTests(createServerTest, ZioStreams).tests() ++
          new ZioHttpCompositionTest(createServerTest).tests() ++
          new ServerWebSocketTests(createServerTest, ZioStreams) {
            override def functionToPipe[A, B](f: A => B): ZioStreams.Pipe[A, B] = in => in.map(f)
            override def emptyPipe[A, B]: ZioStreams.Pipe[A, B] = _ => ZStream.empty
          }.tests() ++
          additionalTests()
      }
  }
}
