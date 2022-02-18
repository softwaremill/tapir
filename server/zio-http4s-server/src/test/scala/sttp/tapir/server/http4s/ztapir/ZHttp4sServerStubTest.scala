package sttp.tapir.server.http4s.ztapir

import sttp.capabilities.zio.ZioStreams
import sttp.client3.testing.SttpBackendStub
import sttp.tapir.integ.cats.CatsMonadError
import sttp.tapir.server.http4s.Http4sServerOptions
import sttp.tapir.server.interceptor.CustomInterceptors
import sttp.tapir.server.tests.{CreateServerStubTest, ServerStubStreamingTest, ServerStubTest}
import zio.interop.catz._
import zio.stream.ZStream
import zio.{Clock, RIO, Runtime}

import scala.concurrent.Future

object ZHttp4sCreateServerStubTest extends CreateServerStubTest[RIO[Clock, *], Http4sServerOptions[RIO[Clock, *], RIO[Clock, *]]] {
  override def customInterceptors: CustomInterceptors[RIO[Clock, *], Http4sServerOptions[RIO[Clock, *], RIO[Clock, *]]] =
    Http4sServerOptions.customInterceptors
  override def stub[R]: SttpBackendStub[RIO[Clock, *], R] = SttpBackendStub(new CatsMonadError[RIO[Clock, *]])
  override def asFuture[A]: RIO[Clock, A] => Future[A] = rio => Runtime.default.unsafeRunToFuture(rio)
}

class ZHttp4sServerStubTest extends ServerStubTest(ZHttp4sCreateServerStubTest)

class ZHttp4sServerStubStreamingTest extends ServerStubStreamingTest(ZHttp4sCreateServerStubTest, ZioStreams) {

  /** Must be an instance of streams.BinaryStream */
  override def sampleStream: Any = ZStream.fromIterable(List("hello"))
}
