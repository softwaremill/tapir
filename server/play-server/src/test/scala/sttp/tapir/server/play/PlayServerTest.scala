package sttp.tapir.server.play

import akka.actor.ActorSystem
import cats.effect.{IO, Resource}
import sttp.monad.FutureMonad
import sttp.tapir.server.tests.{CreateServerTest, ServerAuthenticationTests, ServerBasicTests, ServerMetricsTest, backendResource}
import sttp.tapir.tests.{Test, TestSuite}

class PlayServerTest extends TestSuite {

  def actorSystemResource: Resource[IO, ActorSystem] =
    Resource.make(IO.delay(ActorSystem()))(actorSystem => IO.fromFuture(IO.delay(actorSystem.terminate())).void)

  override def tests: Resource[IO, List[Test]] = backendResource.flatMap { backend =>
    actorSystemResource.map { implicit actorSystem =>
      implicit val m: FutureMonad = new FutureMonad()(actorSystem.dispatcher)

      val interpreter = new PlayTestServerInterpreter()(actorSystem)
      val createServerTest = new CreateServerTest(interpreter)

      new ServerBasicTests(
        backend,
        createServerTest,
        interpreter,
        multipleValueHeaderSupport = false,
        multipartInlineHeaderSupport = false,
        inputStreamSupport = false
      ).tests() ++ new ServerAuthenticationTests(backend, createServerTest).tests() ++
        new ServerMetricsTest(backend, createServerTest).tests()
    }
  }
}
