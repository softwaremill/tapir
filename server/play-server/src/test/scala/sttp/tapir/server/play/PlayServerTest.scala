package sttp.tapir.server.play

import akka.actor.ActorSystem
import cats.effect.{IO, Resource}
import sttp.capabilities.akka.AkkaStreams
import sttp.monad.FutureMonad
import sttp.tapir.server.tests.{
  AllServerTests,
  DefaultCreateServerTest,
  ServerSecurityTests,
  ServerBasicTests,
  ServerMetricsTest,
  ServerMultipartTests,
  ServerStaticContentTests,
  ServerStreamingTests,
  backendResource
}
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
        new ServerMultipartTests(createServerTest, multipartInlineHeaderSupport = false).tests() ++
        new AllServerTests(createServerTest, interpreter, backend, basic = false, multipart = false, reject = false).tests() ++
        new ServerStreamingTests(createServerTest, AkkaStreams).tests() ++
        new PlayServerWithContextTest(backend).tests()
    }
  }
}
