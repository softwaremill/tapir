package sttp.tapir.server.vertx

import sttp.capabilities.zio.ZioStreams
import sttp.client3.testing.SttpBackendStub
import sttp.tapir.server.interceptor.CustomiseInterceptors
import sttp.tapir.server.tests.{CreateServerStubTest, ServerStubStreamingTest, ServerStubTest}
import zio.stream.ZStream
import zio.{Runtime, Task}

import scala.concurrent.Future

object VertxZioCreateServerStubTest extends CreateServerStubTest[Task, VertxZioServerOptions[Task]] {
  override def customiseInterceptors: CustomiseInterceptors[Task, VertxZioServerOptions[Task]] = VertxZioServerOptions.customiseInterceptors
  override def stub[R]: SttpBackendStub[Task, R] = SttpBackendStub(VertxZioServerInterpreter.monadError)
  override def asFuture[A]: Task[A] => Future[A] = task => Runtime.default.unsafeRunToFuture(task)
}

class VertxZioServerStubTest extends ServerStubTest(VertxZioCreateServerStubTest)

class VertxZioServerStubStreamingTest extends ServerStubStreamingTest(VertxZioCreateServerStubTest, ZioStreams) {

  /** Must be an instance of streams.BinaryStream */
  override def sampleStream: Any = ZStream.fromIterable(List("hello"))
}
