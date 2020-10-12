package sttp.tapir.server.play

import akka.actor.ActorSystem
import cats.effect.{IO, Resource}
import sttp.monad.FutureMonad
import sttp.tapir.server.tests.{ServerBasicTests, ServerTests, backendResource}
import sttp.tapir.tests.{Test, TestSuite}

class PlayServerTests extends TestSuite {

  def actorSystemResource: Resource[IO, ActorSystem] =
    Resource.make(IO.delay(ActorSystem()))(actorSystem => IO.fromFuture(IO.delay(actorSystem.terminate())).void)

  override def tests: Resource[IO, List[Test]] = backendResource.flatMap { backend =>
    actorSystemResource.map { implicit actorSystem =>
      implicit val m: FutureMonad = new FutureMonad()(actorSystem.dispatcher)
      val interpreter = new PlayServerInterpreter()(actorSystem)
      val serverTests = new ServerTests(interpreter)

      new ServerBasicTests(
        backend,
        serverTests,
        interpreter,
        multipleValueHeaderSupport = false,
        multipartInlineHeaderSupport = false,
        inputStreamSupport = false
      ).tests()
    }
  }
}
