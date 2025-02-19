package sttp.tapir.server.play

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.{Flow, Sink, Source}
import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global
import com.typesafe.config.ConfigFactory
import org.apache.pekko.util.ByteString
import org.scalatest.matchers.should.Matchers.{fail, _}
import play.api.{Configuration, Mode}
import play.api.http.ParserConfiguration
import play.api.routing.Router
import play.core.server.{DefaultPekkoHttpServerComponents, ServerConfig}
import sttp.capabilities.Streams
import sttp.capabilities.fs2.Fs2Streams
import sttp.capabilities.pekko.PekkoStreams
import sttp.client3._
import sttp.model.{HeaderNames, MediaType, Part, StatusCode}
import sttp.monad.FutureMonad
import sttp.tapir._
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}
import fs2.{Chunk, Stream}
import sttp.capabilities.fs2.Fs2Streams

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class PlayServerTest extends TestSuite {

  def actorSystemResource: Resource[IO, ActorSystem] =
    Resource.make(IO.delay(ActorSystem()))(actorSystem => IO.fromFuture(IO.delay(actorSystem.terminate())).void)

  override def tests: Resource[IO, List[Test]] = backendResource.flatMap { backend =>
    actorSystemResource.map { implicit _actorSystem =>
      implicit val m: FutureMonad = new FutureMonad()(_actorSystem.dispatcher)

      val interpreter = new PlayTestServerInterpreter()(_actorSystem)
      val createServerTest = new DefaultCreateServerTest(backend, interpreter)

      def additionalTests(): List[Test] = List(
        Test("reject big body in multipart request") {
          import sttp.tapir.generic.auto._
          case class A(part1: Part[String])
          val e = endpoint.post.in("hello").in(multipartBody[A]).out(stringBody).serverLogicSuccess(_ => Future.successful("world"))
          val routes = PlayServerInterpreter().toRoutes(e)
          interpreter
            .server(NonEmptyList.of(routes))
            .use { port =>
              basicRequest
                .post(uri"http://localhost:$port/hello")
                .body(Array.ofDim[Byte](1024 * 15000)) // 15M
                .send(backend)
                .map(_.code shouldBe StatusCode.PayloadTooLarge)
                // sometimes the connection is closed before received the response
                .handleErrorWith {
                  case _: SttpClientException.ReadException => IO.pure(succeed)
                  case e                                    => IO.raiseError(e)
                }
            }
            .unsafeToFuture()
        },
        Test("reject big body in normal request") {
          val e = endpoint.post.in("hello").in(stringBody).out(stringBody).serverLogicSuccess(_ => Future.successful("world"))
          val routes = PlayServerInterpreter().toRoutes(e)
          interpreter
            .server(NonEmptyList.of(routes))
            .use { port =>
              basicRequest
                .post(uri"http://localhost:$port/hello")
                .body(Array.ofDim[Byte](1024 * 15000)) // 15M
                .send(backend)
                .map(_.code shouldBe StatusCode.PayloadTooLarge)
                // sometimes the connection is closed before received the response
                .handleErrorWith {
                  case _: SttpClientException.ReadException => IO.pure(succeed)
                  case e                                    => IO.raiseError(e)
                }
            }
            .unsafeToFuture()
        },
        Test("accept string in big multipart") {
          case class A(part1: Part[TapirFile], part2: String)
          implicit val schema: Schema[A] = Schema.derived
          val e = endpoint.post.in("hello").in(multipartBody[A]).out(stringBody).serverLogicSuccess(_ => Future.successful("world"))
          val routes = PlayServerInterpreter().toRoutes(e)
          interpreter
            .server(NonEmptyList.of(routes))
            .use { port =>
              basicRequest
                .post(uri"http://localhost:$port/hello")
                .multipartBody(
                  Part(
                    "part1",
                    ByteArrayBody(Array.ofDim[Byte](ParserConfiguration().maxMemoryBuffer.toInt + 100)),
                    contentType = Some(MediaType.ApplicationOctetStream),
                    fileName = Some("file.bin")
                  ),
                  Part("part2", StringBody("world", "utf-8"))
                )
                .send(backend)
                .map(_.code shouldBe StatusCode.Ok)
                // sometimes the connection is closed before received the response
                .handleErrorWith {
                  case _: SttpClientException.ReadException => IO.pure(succeed)
                  case e                                    => IO.raiseError(e)
                }
            }
            .unsafeToFuture()
        },
        Test("chunked transmission lasts longer than given timeout") {
          val chunkSize = 100
          val beforeSendingSecondChunk: FiniteDuration = 2.second
          val requestTimeout: FiniteDuration = 1.second

          val e =
            endpoint.post
              .in(header[Long](HeaderNames.ContentLength))
              .in(streamTextBody(PekkoStreams)(CodecFormat.TextPlain()))
              .out(header[Long](HeaderNames.ContentLength))
              .out(streamTextBody(PekkoStreams)(CodecFormat.TextPlain()))
              .serverLogicSuccess[Future] { case (length, stream) =>
                Future.successful(length, stream)
              }

          val components: DefaultPekkoHttpServerComponents = new DefaultPekkoHttpServerComponents {
            val initialServerConfig: ServerConfig = ServerConfig(port = Some(0), address = "127.0.0.1", mode = Mode.Test)

            val customConf: Configuration =
              Configuration(
                ConfigFactory.parseString(s"play { server.pekko.requestTimeout=${requestTimeout.toString} }")
              )
            override lazy val serverConfig: ServerConfig =
              initialServerConfig.copy(configuration = customConf.withFallback(initialServerConfig.configuration))
            override lazy val actorSystem: ActorSystem = ActorSystem("tapir", defaultExecutionContext = Some(_actorSystem.dispatcher))
            override lazy val router: Router = Router.from(PlayServerInterpreter().toRoutes(e)).withPrefix("/chunks")
          }

          def createStream(chunkSize: Int, beforeSendingSecondChunk: FiniteDuration): Stream[IO, Byte] = {
            val chunk = Chunk.array(Array.fill(chunkSize)('A'.toByte))
            val initialChunks = Stream.chunk(chunk)
            val delayedChunk = Stream.sleep[IO](beforeSendingSecondChunk) >> Stream.chunk(chunk)
            initialChunks ++ delayedChunk
          }

          val inputStream = createStream(chunkSize, beforeSendingSecondChunk)

          val bind = IO.blocking(components.server)
          Resource.make(bind)(s => IO.blocking(s.stop()))
            .map(_.mainAddress.getPort)
            .use { port =>
              basicRequest
                .post(uri"http://localhost:$port/chunks")
                .contentLength(2 * chunkSize)
                .streamBody(Fs2Streams[IO])(inputStream)
                .send(backend)
                .map{ response =>
                  response.code shouldBe StatusCode.Ok
                  response.contentLength shouldBe Some(2 * chunkSize)
                  response.body shouldBe Right("A" * 2 * chunkSize)
                }
            }
            .unsafeToFuture()
        },
      )

      def drainPekko(stream: PekkoStreams.BinaryStream): Future[Unit] =
        stream.runWith(Sink.ignore).map(_ => ())

      new ServerBasicTests(
        createServerTest,
        interpreter,
        multipleValueHeaderSupport = false,
        inputStreamSupport = false,
        invulnerableToUnsanitizedHeaders = false
      ).tests() ++
        new ServerMultipartTests(createServerTest, partOtherHeaderSupport = false).tests() ++
        new AllServerTests(
          createServerTest,
          interpreter,
          backend,
          basic = false,
          multipart = false,
          options = false
        ).tests() ++
        new ServerStreamingTests(createServerTest).tests(PekkoStreams)(drainPekko) ++
        new PlayServerWithContextTest(backend).tests() ++
        new ServerWebSocketTests(
          createServerTest,
          PekkoStreams,
          autoPing = false,
          handlePong = false,
          decodeCloseRequests = false
        ) {
          override def functionToPipe[A, B](f: A => B): streams.Pipe[A, B] = Flow.fromFunction(f)
          override def emptyPipe[A, B]: Flow[A, B, Any] = Flow.fromSinkAndSource(Sink.ignore, Source.empty)
        }.tests() ++
        additionalTests()
    }
  }

  override def testNameFilter: Option[String] = Some("chunked transmission lasts longer than given timeout")
}
