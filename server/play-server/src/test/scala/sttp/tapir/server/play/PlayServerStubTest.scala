package sttp.tapir.server.play

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer.matFromSystem
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import sttp.capabilities.pekko.PekkoStreams
import sttp.client4.testing.BackendStub
import sttp.monad.FutureMonad
import sttp.tapir.server.interceptor.CustomiseInterceptors
import sttp.tapir.server.tests.{CreateServerStubTest, ServerStubStreamingTest, ServerStubTest}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

class PlayCreateServerStubTest extends CreateServerStubTest[Future, PlayServerOptions] {
  private implicit val actorSystem: ActorSystem = ActorSystem("play-server-stub-test")

  override def customiseInterceptors: CustomiseInterceptors[Future, PlayServerOptions] = {
    import actorSystem.dispatcher
    PlayServerOptions.customiseInterceptors()
  }
  override def stub: BackendStub[Future] = BackendStub(new FutureMonad()(ExecutionContext.global))
  override def asFuture[A]: Future[A] => Future[A] = identity

  override def cleanUp(): Unit = Await.ready(actorSystem.terminate(), 10.seconds)
}

class PlayServerStubTest extends ServerStubTest(new PlayCreateServerStubTest)

class PlayServerStubStreamingTest extends ServerStubStreamingTest(new PlayCreateServerStubTest, PekkoStreams) {

  /** Must be an instance of streams.BinaryStream */
  override def sampleStream: Any = Source.single(ByteString("hello"))
}
