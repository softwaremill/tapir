package sttp.tapir.server.pekkohttp

import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import sttp.capabilities.pekko.PekkoStreams
import sttp.client4.testing.BackendStub
import sttp.monad.FutureMonad
import sttp.tapir.server.interceptor.CustomiseInterceptors
import sttp.tapir.server.tests.{CreateServerStubTest, ServerStubStreamingTest, ServerStubTest}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object PekkoCreateServerStubTest extends CreateServerStubTest[Future, PekkoHttpServerOptions] {
  override def customiseInterceptors: CustomiseInterceptors[Future, PekkoHttpServerOptions] = PekkoHttpServerOptions.customiseInterceptors
  override def stub: BackendStub[Future] = BackendStub(new FutureMonad())
  override def asFuture[A]: Future[A] => Future[A] = identity
}

class PekkoHttpServerStubTest extends ServerStubTest(PekkoCreateServerStubTest)

class PekkoHttpServerStubStreamingTest extends ServerStubStreamingTest(PekkoCreateServerStubTest, PekkoStreams) {

  /** Must be an instance of streams.BinaryStream */
  override def sampleStream: Any = Source.single(ByteString("hello"))
}
