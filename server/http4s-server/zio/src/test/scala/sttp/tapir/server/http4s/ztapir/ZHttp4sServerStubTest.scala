package sttp.tapir.server.http4s.ztapir

import sttp.capabilities.zio.ZioStreams
import sttp.client3.testing.SttpBackendStub
import sttp.tapir.integ.cats.CatsMonadError
import sttp.tapir.server.http4s.Http4sServerOptions
import sttp.tapir.server.interceptor.CustomiseInterceptors
import sttp.tapir.server.tests.{CreateServerStubTest, ServerStubStreamingTest, ServerStubTest}
import zio.interop.catz._
import zio.stream.ZStream
import zio.{Runtime, Task}

import scala.concurrent.Future

object ZHttp4sCreateServerStubTest extends CreateServerStubTest[Task, Http4sServerOptions[Task]] {
  override def customiseInterceptors: CustomiseInterceptors[Task, Http4sServerOptions[Task]] =
    Http4sServerOptions.customiseInterceptors
  override def stub[R]: SttpBackendStub[Task, R] = SttpBackendStub(new CatsMonadError[Task])
  override def asFuture[A]: Task[A] => Future[A] = rio => Runtime.default.unsafeRunToFuture(rio)
}

class ZHttp4sServerStubTest extends ServerStubTest(ZHttp4sCreateServerStubTest)

class ZHttp4sServerStubStreamingTest extends ServerStubStreamingTest(ZHttp4sCreateServerStubTest, ZioStreams) {

  /** Must be an instance of streams.BinaryStream */
  override def sampleStream: Any = ZStream.fromIterable(List("hello"))
}
