package sttp.tapir.server.ziohttp

import sttp.capabilities.zio.ZioStreams
import sttp.client3.testing.SttpBackendStub
import sttp.tapir.server.interceptor.CustomInterceptors
import sttp.tapir.server.tests.{CreateServerStubTest, ServerStubStreamingTest, ServerStubTest}
import sttp.tapir.ztapir.RIOMonadError
import zio.stream.ZStream
import zio.{Runtime, Task}

import scala.concurrent.Future

object ZioHttpCreateServerStubTest extends CreateServerStubTest[Task, ZioHttpServerOptions[Any]] {
  override def customInterceptors: CustomInterceptors[Task, ZioHttpServerOptions[Any]] = ZioHttpServerOptions.customInterceptors
  override def stub[R]: SttpBackendStub[Task, R] = SttpBackendStub(new RIOMonadError[Any])
  override def asFuture[A]: Task[A] => Future[A] = task => Runtime.default.unsafeRunToFuture(task)
}

class ZioHttpServerStubTest extends ServerStubTest(ZioHttpCreateServerStubTest)

class ZioHttpServerStubStreamingTest extends ServerStubStreamingTest(ZioHttpCreateServerStubTest, ZioStreams) {

  /** Must be an instance of streams.BinaryStream */
  override def sampleStream: Any = ZStream.fromIterable(List("hello"))
}
