package sttp.tapir.server.ziohttp

import sttp.capabilities.zio.ZioStreams
import sttp.client4.testing.BackendStub
import sttp.tapir.server.interceptor.CustomiseInterceptors
import sttp.tapir.server.tests.{CreateServerStubTest, ServerStubStreamingTest, ServerStubTest}
import sttp.tapir.ztapir.RIOMonadError
import zio.stream.ZStream
import zio.{Runtime, Task, Unsafe}

import scala.concurrent.Future

object ZioHttpCreateServerStubTest extends CreateServerStubTest[Task, ZioHttpServerOptions[Any]] {
  override def customiseInterceptors: CustomiseInterceptors[Task, ZioHttpServerOptions[Any]] = ZioHttpServerOptions.customiseInterceptors
  override def stub: BackendStub[Task] = BackendStub(new RIOMonadError[Any])
  override def asFuture[A]: Task[A] => Future[A] = task => Unsafe.unsafe(implicit u => Runtime.default.unsafe.runToFuture(task))
}

class ZioHttpServerStubTest extends ServerStubTest(ZioHttpCreateServerStubTest)

class ZioHttpServerStubStreamingTest extends ServerStubStreamingTest(ZioHttpCreateServerStubTest, ZioStreams) {

  /** Must be an instance of streams.BinaryStream */
  override def sampleStream: Any = ZStream.fromIterable(List("hello"))
}
