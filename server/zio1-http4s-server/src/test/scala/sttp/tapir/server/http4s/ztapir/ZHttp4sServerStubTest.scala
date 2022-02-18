package sttp.tapir.server.http4s.ztapir

import sttp.capabilities.zio.ZioStreams
import sttp.client3.testing.SttpBackendStub
import sttp.tapir.integ.cats.CatsMonadError
import sttp.tapir.server.http4s.Http4sServerOptions
import sttp.tapir.server.interceptor.CustomInterceptors
import sttp.tapir.server.tests.{CreateServerStubTest, ServerStubInterpreterStreamingTest, ServerStubInterpreterTest}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.interop.catz._
import zio.stream.ZStream
import zio.{RIO, Runtime}

import scala.concurrent.Future

object ZHttp4sCreateServerStubTest
    extends CreateServerStubTest[
      RIO[Clock with Blocking, *],
      Http4sServerOptions[RIO[Clock with Blocking, *], RIO[Clock with Blocking, *]]
    ] {
  override def customInterceptors
      : CustomInterceptors[RIO[Clock with Blocking, *], Http4sServerOptions[RIO[Clock with Blocking, *], RIO[Clock with Blocking, *]]] =
    Http4sServerOptions
      .customInterceptors[RIO[Clock with Blocking, *], RIO[Clock with Blocking, *]]

  override def stub[R]: SttpBackendStub[RIO[Clock with Blocking, *], R] = SttpBackendStub(new CatsMonadError[RIO[Clock with Blocking, *]])
  override def asFuture[A]: RIO[Clock with Blocking, A] => Future[A] = rio => Runtime.default.unsafeRunToFuture(rio)
}

class ZHttp4sServerStubTest extends ServerStubInterpreterTest(ZHttp4sCreateServerStubTest)

class ZHttp4sServerStubStreamingTest extends ServerStubInterpreterStreamingTest(ZHttp4sCreateServerStubTest, ZioStreams) {

  /** Must be an instance of streams.BinaryStream */
  override def sampleStream: Any = ZStream.fromIterable(List("hello"))
}
