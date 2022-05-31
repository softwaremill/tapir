package sttp.tapir.server.vertx

import sttp.capabilities.zio.ZioStreams
import sttp.client3.testing.SttpBackendStub
import sttp.tapir.server.interceptor.CustomiseInterceptors
import sttp.tapir.server.tests.{CreateServerStubTest, ServerStubStreamingTest, ServerStubTest}
import zio.stream.ZStream
import zio.{Runtime, RIO}
import zio.blocking.Blocking

import scala.concurrent.Future

object VertxZioCreateServerStubTest extends CreateServerStubTest[RIO[Blocking, *], VertxZioServerOptions[RIO[Blocking, *]]] {
  override def customiseInterceptors: CustomiseInterceptors[RIO[Blocking, *], VertxZioServerOptions[RIO[Blocking, *]]] = VertxZioServerOptions.customiseInterceptors
  override def stub[R]: SttpBackendStub[RIO[Blocking, *], R] = SttpBackendStub(VertxZioServerInterpreter.monadError)
  override def asFuture[A]: RIO[Blocking, A] => Future[A] = task => Runtime.default.unsafeRunToFuture(task)
}

class VertxZioServerStubTest extends ServerStubTest(VertxZioCreateServerStubTest)

class VertxZioServerStubStreamingTest extends ServerStubStreamingTest(VertxZioCreateServerStubTest, ZioStreams) {

  /** Must be an instance of streams.BinaryStream */
  override def sampleStream: Any = ZStream.fromIterable(List("hello"))
}
