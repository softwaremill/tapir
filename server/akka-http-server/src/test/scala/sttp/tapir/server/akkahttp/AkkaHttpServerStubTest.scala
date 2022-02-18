package sttp.tapir.server.akkahttp

import akka.stream.scaladsl.Source
import akka.util.ByteString
import sttp.capabilities.akka.AkkaStreams
import sttp.client3.testing.SttpBackendStub
import sttp.monad.FutureMonad
import sttp.tapir.server.interceptor.CustomInterceptors
import sttp.tapir.server.tests.{CreateServerStubTest, ServerStubInterpreterStreamingTest, ServerStubInterpreterTest}

import scala.concurrent.ExecutionContext.global
import scala.concurrent.Future

object AkkaCreateServerStubTest extends CreateServerStubTest[Future, AkkaHttpServerOptions] {
  override def customInterceptors: CustomInterceptors[Future, AkkaHttpServerOptions] = AkkaHttpServerOptions.customInterceptors
  override def stub[R]: SttpBackendStub[Future, R] = SttpBackendStub(new FutureMonad()(global))
  override def asFuture[A]: Future[A] => Future[A] = identity
}

class AkkaHttpServerStubTest extends ServerStubInterpreterTest(AkkaCreateServerStubTest)

class AkkaHttpServerStubStreamingTest extends ServerStubInterpreterStreamingTest(AkkaCreateServerStubTest, AkkaStreams) {

  /** Must be an instance of streams.BinaryStream */
  override def sampleStream: Any = Source.single(ByteString("hello"))
}
