package sttp.tapir.server.ziohttp

import sttp.capabilities.zio.ZioStreams
import sttp.client3.testing.SttpBackendStub
import sttp.tapir.server.interceptor.CustomiseInterceptors
import sttp.tapir.server.tests.{CreateServerStubTest, ServerStubStreamingTest, ServerStubTest}
import sttp.tapir.ztapir.RIOMonadError
import zio.stream.ZStream
import zio.{Runtime, Schedule, Task}

import scala.concurrent.Future

object ZioHttpCreateServerStubTest extends CreateServerStubTest[Task, ZioHttpServerOptions[Any]] {
  override def customiseInterceptors: CustomiseInterceptors[Task, ZioHttpServerOptions[Any]] = ZioHttpServerOptions.customiseInterceptors
  override def stub[R]: SttpBackendStub[Task, R] = SttpBackendStub(new RIOMonadError[Any])
  override def asFuture[A]: Task[A] => Future[A] = task => Runtime.default.unsafeRunToFuture(task)
}

class ZioHttpServerStubTest extends ServerStubTest(ZioHttpCreateServerStubTest)

class ZioHttpServerStubStreamingTest extends ServerStubStreamingTest(ZioHttpCreateServerStubTest, ZioStreams) {

  /** Must be an instance of streams.BinaryStream */
  override def sampleStream: Any = ZStream("1").repeat(Schedule.forever).take(60000 * 1024)
}
