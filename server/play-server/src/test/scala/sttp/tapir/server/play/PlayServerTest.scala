package sttp.tapir.server.play

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Sink, Source}
import cats.effect.{IO, Resource}
import sttp.capabilities.akka.AkkaStreams
import sttp.monad.FutureMonad
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}

class PlayServerTest extends TestSuite {

  def actorSystemResource: Resource[IO, ActorSystem] =
    Resource.make(IO.delay(ActorSystem()))(actorSystem => IO.fromFuture(IO.delay(actorSystem.terminate())).void)

  override def tests: Resource[IO, List[Test]] = backendResource.flatMap { backend =>
    actorSystemResource.map { implicit actorSystem =>
      implicit val m: FutureMonad = new FutureMonad()(actorSystem.dispatcher)

      val interpreter = new PlayTestServerInterpreter()(actorSystem)
      val createServerTest = new DefaultCreateServerTest(backend, interpreter)

      new ServerBasicTests(
        createServerTest,
        interpreter,
        multipleValueHeaderSupport = false,
        inputStreamSupport = false,
        invulnerableToUnsanitizedHeaders = false
      ).tests() ++
        new ServerMultipartTests(createServerTest, partOtherHeaderSupport = false).tests() ++
        new AllServerTests(createServerTest, interpreter, backend, basic = false, multipart = false, reject = false).tests() ++
        new ServerStreamingTests(createServerTest, AkkaStreams).tests() ++
        new PlayServerWithContextTest(backend).tests() ++
        new ServerWebSocketTests(createServerTest, AkkaStreams) {
          override def functionToPipe[A, B](f: A => B): streams.Pipe[A, B] = Flow.fromFunction(f)
          override def emptyPipe[A, B]: Flow[A, B, Any] = Flow.fromSinkAndSource(Sink.ignore, Source.empty)
        }.tests()
    }
  }
}
