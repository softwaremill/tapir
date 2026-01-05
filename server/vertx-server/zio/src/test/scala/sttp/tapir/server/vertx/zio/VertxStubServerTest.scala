package sttp.tapir.server.vertx.zio

import sttp.capabilities.zio.ZioStreams
import sttp.client4.testing.BackendStub
import sttp.tapir.server.interceptor.CustomiseInterceptors
import sttp.tapir.server.tests.{CreateServerStubTest, ServerStubStreamingTest, ServerStubTest}
import _root_.zio.stream.ZStream
import _root_.zio.{Runtime, Task, Unsafe}
import sttp.tapir.ztapir.RIOMonadError

import scala.concurrent.Future

object VertxZioCreateServerStubTest extends CreateServerStubTest[Task, VertxZioServerOptions[Any]] {
  override def customiseInterceptors: CustomiseInterceptors[Task, VertxZioServerOptions[Any]] = VertxZioServerOptions.customiseInterceptors
  override def stub: BackendStub[Task] = BackendStub(new RIOMonadError[Any])
  override def asFuture[A]: Task[A] => Future[A] = task => Unsafe.unsafe(implicit u => Runtime.default.unsafe.runToFuture(task))
}

class VertxZioServerStubTest extends ServerStubTest(VertxZioCreateServerStubTest)

class VertxZioServerStubStreamingTest extends ServerStubStreamingTest(VertxZioCreateServerStubTest, ZioStreams) {

  /** Must be an instance of streams.BinaryStream */
  override def sampleStream: Any = ZStream.fromIterable(List("hello"))
}
