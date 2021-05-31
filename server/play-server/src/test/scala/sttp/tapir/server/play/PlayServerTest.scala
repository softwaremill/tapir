package sttp.tapir.server.play

import akka.actor.ActorSystem
import cats.effect.{IO, Resource}
import sttp.monad.FutureMonad
import sttp.tapir.server.tests.{
  DefaultCreateServerTest,
  ServerAuthenticationTests,
  ServerBasicTests,
  ServerFileMultipartTests,
  ServerMetricsTest,
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
        inputStreamSupport = false
      ).tests() ++
        new ServerFileMultipartTests(createServerTest, multipartInlineHeaderSupport = false).tests()
      new ServerAuthenticationTests(createServerTest).tests() ++
        new ServerMetricsTest(createServerTest).tests()
    }
  }
}
