package sttp.tapir.server.vertx.zio

import sttp.capabilities.zio.ZioStreams
import sttp.client3.testing.SttpBackendStub
import sttp.tapir.server.interceptor.CustomiseInterceptors
import sttp.tapir.server.tests.{CreateServerStubTest, ServerStubStreamingTest, ServerStubTest}
import _root_.zio.stream.ZStream
import _root_.zio.{RIO, Runtime}
import _root_.zio.blocking.Blocking
import sttp.tapir.ztapir.RIOMonadError

import scala.concurrent.Future

object VertxZioCreateServerStubTest extends CreateServerStubTest[RIO[Blocking, *], VertxZioServerOptions[Blocking]] {
  override def customiseInterceptors: CustomiseInterceptors[RIO[Blocking, *], VertxZioServerOptions[Blocking]] =
    VertxZioServerOptions.customiseInterceptors
  override def stub[R]: SttpBackendStub[RIO[Blocking, *], R] = SttpBackendStub(new RIOMonadError[Blocking])
  override def asFuture[A]: RIO[Blocking, A] => Future[A] = task => Runtime.default.unsafeRunToFuture(task)
}

class VertxZioServerStubTest extends ServerStubTest(VertxZioCreateServerStubTest)

class VertxZioServerStubStreamingTest extends ServerStubStreamingTest(VertxZioCreateServerStubTest, ZioStreams) {

  /** Must be an instance of streams.BinaryStream */
  override def sampleStream: Any = ZStream.fromIterable(List("hello"))
}
