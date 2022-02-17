package sttp.tapir.server.play

import akka.actor.ActorSystem
import akka.stream.Materializer.matFromSystem
import org.scalatest.BeforeAndAfterAll
import sttp.capabilities.WebSockets
import sttp.capabilities.akka.AkkaStreams
import sttp.client3.testing.SttpBackendStub
import sttp.monad.FutureMonad
import sttp.tapir.server.interceptor.CustomInterceptors
import sttp.tapir.server.tests.ServerStubInterpreterTest

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

class PlayServerStubTest extends ServerStubInterpreterTest[Future, AkkaStreams with WebSockets, PlayServerOptions] with BeforeAndAfterAll {
  private implicit val actorSystem: ActorSystem = ActorSystem("play-server-stub-test")

  override def customInterceptors: CustomInterceptors[Future, PlayServerOptions] = {
    import actorSystem.dispatcher
    PlayServerOptions.customInterceptors
  }
  override def stub: SttpBackendStub[Future, AkkaStreams with WebSockets] = SttpBackendStub(new FutureMonad())
  override def asFuture[A]: Future[A] => Future[A] = identity

  override protected def afterAll(): Unit = {
    Await.ready(actorSystem.terminate(), 10.seconds)
  }

}
