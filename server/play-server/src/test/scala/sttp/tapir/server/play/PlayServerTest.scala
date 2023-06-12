package sttp.tapir.server.play

import akka.actor.ActorSystem
import enumeratum._
import akka.stream.scaladsl.{Flow, Sink, Source}
import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global
import org.scalatest.matchers.should.Matchers._
import play.api.http.ParserConfiguration
import sttp.capabilities.akka.AkkaStreams
import sttp.client3._
import sttp.model.{MediaType, Part, StatusCode}
import sttp.monad.FutureMonad
import sttp.tapir._
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}

import scala.concurrent.Future
import sttp.tapir.codec.enumeratum.TapirCodecEnumeratum
import sttp.tapir.server.interceptor.decodefailure.DefaultDecodeFailureHandler

class PlayServerTest extends TestSuite {

  def actorSystemResource: Resource[IO, ActorSystem] =
    Resource.make(IO.delay(ActorSystem()))(actorSystem => IO.fromFuture(IO.delay(actorSystem.terminate())).void)

  override def tests: Resource[IO, List[Test]] = backendResource.flatMap { backend =>
    actorSystemResource.map { implicit actorSystem =>
      implicit val m: FutureMonad = new FutureMonad()(actorSystem.dispatcher)

      val interpreter = new PlayTestServerInterpreter()(actorSystem)
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
        }
      )

      new ServerBasicTests(
        createServerTest,
        interpreter,
        multipleValueHeaderSupport = false,
        inputStreamSupport = false,
        invulnerableToUnsanitizedHeaders = false
      ).tests() ++
        new ServerMultipartTests(createServerTest, partOtherHeaderSupport = false).tests() ++
        new AllServerTests(createServerTest, interpreter, backend, basic = false, multipart = false, options = false).tests() ++
        new ServerStreamingTests(createServerTest, AkkaStreams).tests() ++
        new PlayServerWithContextTest(backend).tests() ++
        new ServerWebSocketTests(createServerTest, AkkaStreams) {
          override def functionToPipe[A, B](f: A => B): streams.Pipe[A, B] = Flow.fromFunction(f)
          override def emptyPipe[A, B]: Flow[A, B, Any] = Flow.fromSinkAndSource(Sink.ignore, Source.empty)
        }.tests() ++
        additionalTests()
    }
  }
}
