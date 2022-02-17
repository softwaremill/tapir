package sttp.tapir.server.http4s.ztapir

import sttp.capabilities.WebSockets
import sttp.capabilities.zio.ZioStreams
import sttp.client3.testing.SttpBackendStub
import sttp.tapir.integ.cats.CatsMonadError
import sttp.tapir.server.http4s.Http4sServerOptions
import sttp.tapir.server.interceptor.CustomInterceptors
import sttp.tapir.server.tests.ServerStubInterpreterTest
import zio.interop.catz._
import zio.{Clock, RIO, Runtime}

import scala.concurrent.Future

class ZHttp4sServerStubTest
    extends ServerStubInterpreterTest[RIO[Clock, *], ZioStreams with WebSockets, Http4sServerOptions[RIO[Clock, *], RIO[Clock, *]]] {
  override def customInterceptors: CustomInterceptors[RIO[Clock, *], Http4sServerOptions[RIO[Clock, *], RIO[Clock, *]]] =
    Http4sServerOptions.customInterceptors
  override def stub: SttpBackendStub[RIO[Clock, *], ZioStreams with WebSockets] = SttpBackendStub(new CatsMonadError[RIO[Clock, *]])
  override def asFuture[A]: RIO[Clock, A] => Future[A] = rio => Runtime.default.unsafeRunToFuture(rio)
}
