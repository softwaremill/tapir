package sttp.tapir.server.ziohttp

import sttp.capabilities.zio.ZioStreams
import sttp.client3.testing.SttpBackendStub
import sttp.tapir.server.interceptor.CustomInterceptors
import sttp.tapir.server.tests.ServerStubInterpreterTest
import sttp.tapir.ztapir.RIOMonadError
import zio.{Runtime, Task}

import scala.concurrent.Future

class ZioHttpServerStubTest extends ServerStubInterpreterTest[Task, ZioStreams, ZioHttpServerOptions[Any]] {
  override def customInterceptors: CustomInterceptors[Task, ZioHttpServerOptions[Any]] = ZioHttpServerOptions.customInterceptors
  override def stub: SttpBackendStub[Task, ZioStreams] = SttpBackendStub(new RIOMonadError[Any])
  override def asFuture[A]: Task[A] => Future[A] = task => Runtime.default.unsafeRunToFuture(task)
}
