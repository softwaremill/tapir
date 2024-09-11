package sttp.tapir.server.ziohttp

import cats.effect.IO
import cats.effect.Resource
import cats.implicits.toTraverseOps
import io.netty.channel.ChannelFactory
import io.netty.channel.ServerChannel
import org.scalatest.Assertion
import org.scalatest.Exceptional
import org.scalatest.FutureOutcome
import org.scalatest.matchers.should.Matchers._
import sttp.capabilities.zio.ZioStreams
import sttp.client3._
import sttp.client3.testing.SttpBackendStub
import sttp.model.MediaType
import sttp.monad.MonadError
import sttp.tapir.PublicEndpoint
import sttp.tapir._
import sttp.tapir.server.stub.TapirStubInterpreter
import sttp.tapir.server.tests._
import sttp.tapir.tests.Test
import sttp.tapir.tests.TestSuite
import sttp.tapir.ztapir.RIOMonadError
import sttp.tapir.ztapir.RichZEndpoint
import sttp.ws.WebSocket
import sttp.ws.WebSocketFrame
import zio.Promise
import zio.Ref
import zio.Runtime
import zio.Task
import zio.UIO
import zio.Unsafe
import zio.ZEnvironment
import zio.ZIO
import zio.ZLayer
import zio.http.Middleware
import zio.http.Path
import zio.http.Request
import zio.http.URL
import zio.interop.catz._
import zio.stream
import zio.stream.ZPipeline
import zio.stream.ZStream

import java.nio.charset.Charset
import java.time
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import zio.stream.ZSink
import zio.http.netty.NettyConfig
import zio.http.netty.ChannelType
import zio.http.netty.server.ServerEventLoopGroups
import _root_.zio.http.netty.TestChannelFactories

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
      .scoped[IO, Any, ZEnvironment[ServerEventLoopGroups with ChannelFactory[ServerChannel]]]({
        val eventConfig = ZLayer.succeed(
          NettyConfig.default.bossGroup(
            NettyConfig.BossGroup(
              channelType = ChannelType.AUTO,
              nThreads = 0,
              shutdownQuietPeriodDuration = zio.Duration.fromSeconds(0),
              shutdownTimeOutDuration = zio.Duration.fromSeconds(0)
            )
          )
        )

        val channelConfig: ZLayer[Any, Nothing, ChannelType.Config] = eventConfig
        val channelFactory = (channelConfig >>> TestChannelFactories.config)
        val groups = (eventConfig >>> ServerEventLoopGroups.live)
        (groups ++ channelFactory)
      }.build)
      .map { environment =>
        val groups = environment.get[ServerEventLoopGroups]
        val factory = environment.get[ChannelFactory[ServerChannel]]
        val interpreter = new ZioHttpTestServerInterpreter(groups, factory)
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
              route = int @@ Middleware.allowZIO((_: Request) => p.succeed(()).as(true))
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
                .toHttp(ep) @@ Middleware.allowZIO((_: Request) => ref.update(_ + 1).as(true))
              result <- route
                .runZIO(Request.get(url = URL(Path.empty / "p1")))
                .flatMap(response => response.body.asString)
                .map(_ shouldBe "2")
                .catchAll(_ => ZIO.succeed(fail("Unable to extract body from Http response")))
            } yield result

            Unsafe.unsafe(implicit u => r.unsafe.runToFuture(test))
          },
          Test("zio http middlewares only run once, with two endpoints") {
            val test: UIO[Assertion] = for {
              ref <- Ref.make("")
              ep1 = endpoint.get.in("p1").out(stringBody).zServerLogic[Any](_ => ref.updateAndGet(_ + "1"))
              ep2 = endpoint.get.in("p2").out(stringBody).zServerLogic[Any](_ => ref.updateAndGet(_ + "2"))
              route = ZioHttpInterpreter().toHttp(ep1) ++ ZioHttpInterpreter().toHttp(ep2)
              app = route @@ Middleware.allowZIO((_: Request) => ref.update(_ + "M").as(true))
              _ <- app
                .runZIO(Request.get(url = URL(Path.empty / "p1")))
                .flatMap(response => response.body.asString)
                .map(_ shouldBe "M1")
                .catchAll(_ => ZIO.succeed(fail("Unable to extract body from Http response")))
              _ <- ref.set("")
              result <- app
                .runZIO(Request.get(url = URL(Path.empty / "p2")))
                .flatMap(response => response.body.asString)
                .map(_ shouldBe "M2")
                .catchAll(_ => ZIO.succeed(fail("Unable to extract body from Http response")))
            } yield result

            Unsafe.unsafe(implicit u => r.unsafe.runToFuture(test))
          },
          // https://github.com/softwaremill/tapir/issues/3907
          Test("extractFromRequest in the middle") {
            val ep = endpoint.get
              .in(path[String])
              .in(extractFromRequest(_.method))
              .in("test" / path[String])
              .out(stringBody)
              .zServerLogic[Any](_ => ZIO.succeed("works"))
            val route = ZioHttpInterpreter().toHttp(ep)

            val test: UIO[Assertion] = route
              .runZIO(Request.get(url = URL(Path.empty / "p1" / "test" / "p2")))
              .flatMap(response => response.body.asString)
              .map(_ shouldBe "works")
              .catchAll(_ => ZIO.succeed[Assertion](fail("Unable to extract body from Http response")))

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
                  ZIO.succeed({
                    stream
                      .via(ZPipeline.utf8Decode)
                      .via(ZPipeline.splitLines)
                      .via(ZPipeline.intersperse(java.lang.System.lineSeparator()))
                      .via(ZPipeline.utf8Encode)
                  })
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
          },
          createServerTest.testServer(
            endpoint
              .out(
                webSocketBody[String, CodecFormat.TextPlain, String, CodecFormat.TextPlain]
                  .apply(ZioStreams)
                  .autoPing(Some((1.second, WebSocketFrame.ping)))
              ),
            "auto pings"
          )((_: Unit) => ZIO.right((in: stream.Stream[Throwable, String]) => in.map(v => s"echo $v"))) { (backend, baseUri) =>
            basicRequest
              .response(asWebSocket { (ws: WebSocket[IO]) =>
                List(ws.receive().timeout(60.seconds), ws.receive().timeout(60.seconds)).sequence
              })
              .get(baseUri.scheme("ws"))
              .send(backend)
              .map(_.body should matchPattern { case Right(List(WebSocketFrame.Ping(_), WebSocketFrame.Ping(_))) => })
          },
          createServerTest.testServer(
            endpoint
              .out(
                webSocketBody[String, CodecFormat.TextPlain, String, CodecFormat.TextPlain]
                  .apply(ZioStreams)
                  .autoPing(None)
              ),
            "ping-pong echo"
          )((_: Unit) => ZIO.right((in: stream.Stream[Throwable, String]) => in.map(v => s"echo $v"))) { (backend, baseUri) =>
            basicRequest
              .response(asWebSocket { (ws: WebSocket[IO]) =>
                for {
                  _ <- ws.send(WebSocketFrame.ping)
                  m2 <- ws.receive()
                } yield List(m2)
              })
              .get(baseUri.scheme("ws"))
              .send(backend)
              .map { response =>
                response.body should matchPattern { case Right(List(_: WebSocketFrame.Pong)) => }
              }
          }
        )

        implicit val m: MonadError[Task] = new RIOMonadError[Any]

        def drainZStream(zStream: ZioStreams.BinaryStream): Task[Unit] =
          zStream.run(ZSink.drain)

        new ServerBasicTests(
          createServerTest,
          interpreter,
          multipleValueHeaderSupport = false,
          supportsMultipleSetCookieHeaders = false,
          invulnerableToUnsanitizedHeaders = false,
          maxContentLength = true
        ).tests() ++
          new AllServerTests(
            createServerTest,
            interpreter,
            backend,
            basic = false,
            staticContent = true,
            multipart = false,
            file = true,
            options = false
          ).tests() ++
          new ServerStreamingTests(createServerTest).tests(ZioStreams)(drainZStream) ++
          new ZioHttpCompositionTest(createServerTest).tests() ++
          new ServerWebSocketTests(
            createServerTest,
            ZioStreams,
            autoPing = true,
            failingPipe = false,
            handlePong = false,
            frameConcatenation = false
          ) {
            override def functionToPipe[A, B](f: A => B): ZioStreams.Pipe[A, B] = in => in.map(f)
            override def emptyPipe[A, B]: ZioStreams.Pipe[A, B] = _ => ZStream.empty
          }.tests() ++
          additionalTests()
      }
  }
}
