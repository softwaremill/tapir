package sttp.tapir.server.http4s.ztapir

import cats.effect._
import org.scalatest.OptionValues
import sttp.capabilities.zio.ZioStreams
import sttp.client3._
import sttp.model.sse.ServerSentEvent
import sttp.monad.MonadError
import sttp.tapir._
import sttp.tapir.integ.cats.CatsMonadError
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}
import zio.{RIO, UIO}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.interop.catz._
import org.scalatest.matchers.should.Matchers._
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.server.http4s.Http4sServerSentEvents

import java.util.UUID
import scala.util.Random

class ZHttp4sServerTest extends TestSuite with OptionValues {

  override def tests: Resource[IO, List[Test]] = backendResource.map { backend =>
    implicit val m: MonadError[RIO[Clock with Blocking, *]] = new CatsMonadError[RIO[Clock with Blocking, *]]

    val interpreter = new ZHttp4sTestServerInterpreter()
    val createServerTest = new DefaultCreateServerTest(backend, interpreter)

    def randomUUID = Some(UUID.randomUUID().toString)
    val sse1 = ServerSentEvent(randomUUID, randomUUID, randomUUID, Some(Random.nextInt(200)))
    val sse2 = ServerSentEvent(randomUUID, randomUUID, randomUUID, Some(Random.nextInt(200)))

    def additionalTests(): List[Test] = List(
      createServerTest.testServer(
        endpoint.out(serverSentEventsBody),
        "Send and receive SSE"
      )((_: Unit) => UIO(Right(zio.stream.Stream(sse1, sse2)))) { (backend, baseUri) =>
        basicRequest
          .response(asStream[IO, List[ServerSentEvent], Fs2Streams[IO]](Fs2Streams[IO]) { stream =>
            Http4sServerSentEvents
              .parseBytesToSSE[IO]
              .apply(stream)
              .compile
              .toList
          })
          .get(baseUri)
          .send(backend)
          .map(_.body.right.toOption.value shouldBe List(sse1, sse2))
      }
    )

    new ServerBasicTests(createServerTest, interpreter).tests() ++
      new ServerFileMultipartTests(createServerTest).tests() ++
      new ServerStreamingTests(createServerTest, ZioStreams).tests() ++
      new ServerWebSocketTests(createServerTest, ZioStreams) {
        override def functionToPipe[A, B](f: A => B): streams.Pipe[A, B] = in => in.map(f)
      }.tests() ++
      new ServerAuthenticationTests(createServerTest).tests() ++
      new ServerMetricsTest(createServerTest).tests() ++
      new ServerRejectTests(createServerTest, interpreter).tests() ++
      new ServerStaticContentTests(interpreter, backend).tests() ++
      additionalTests()
  }
}
