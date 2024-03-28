package sttp.tapir.server.http4s.ztapir

import cats.effect._
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers._
import sttp.capabilities.fs2.Fs2Streams
import sttp.capabilities.zio.ZioStreams
import sttp.client3._
import sttp.model.sse.ServerSentEvent
import sttp.monad.MonadError
import sttp.tapir._
import sttp.tapir.integ.cats.effect.CatsMonadError
import sttp.tapir.server.http4s.Http4sServerSentEvents
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}
import zio.interop.catz._
import zio.stream.{ZSink, ZStream}
import zio.{Task, ZIO}

import java.util.UUID
import scala.util.Random

class ZHttp4sServerTest extends TestSuite with OptionValues {

  override def tests: Resource[IO, List[Test]] = backendResource.map { backend =>
    implicit val m: MonadError[Task] = new CatsMonadError[Task]

    val interpreter = new ZHttp4sTestServerInterpreter()
    val createServerTest = new DefaultCreateServerTest(backend, interpreter)

    def randomUUID = Some(UUID.randomUUID().toString)
    val sse1 = ServerSentEvent(randomUUID, randomUUID, randomUUID, Some(Random.nextInt(200)))
    val sse2 = ServerSentEvent(randomUUID, randomUUID, randomUUID, Some(Random.nextInt(200)))

    def additionalTests(): List[Test] = List(
      createServerTest.testServer(
        endpoint.out(serverSentEventsBody),
        "Send and receive SSE"
      )((_: Unit) => ZIO.right(ZStream(sse1, sse2))) { (backend, baseUri) =>
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
          .map(_.body.toOption.value shouldBe List(sse1, sse2))
      }
    )
    def drainZStream(zStream: ZioStreams.BinaryStream): Task[Unit] =
      zStream.run(ZSink.drain)

    new AllServerTests(createServerTest, interpreter, backend).tests() ++
      new ServerStreamingTests(createServerTest).tests(ZioStreams)(drainZStream) ++
      new ServerWebSocketTests(
        createServerTest,
        ZioStreams,
        autoPing = true,
        failingPipe = false,
        handlePong = false,
        rejectNonWsEndpoints = false
      ) {
        override def functionToPipe[A, B](f: A => B): streams.Pipe[A, B] = in => in.map(f)
        override def emptyPipe[A, B]: streams.Pipe[A, B] = _ => ZStream.empty
      }.tests() ++
      additionalTests()
  }
}
