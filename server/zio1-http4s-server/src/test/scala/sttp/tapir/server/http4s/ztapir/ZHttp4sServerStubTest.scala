package sttp.tapir.server.http4s.ztapir

import sttp.capabilities.WebSockets
import sttp.capabilities.zio.ZioStreams
import sttp.monad.MonadError
import sttp.tapir.integ.cats.CatsMonadError
import sttp.tapir.server.http4s.Http4sServerOptions
import sttp.tapir.server.interceptor.CustomInterceptors
import sttp.tapir.server.tests.ServerStubInterpreterTest
import zio.blocking.Blocking
import zio.clock.Clock
import zio.interop.catz._
import zio.{RIO, Runtime}

import scala.concurrent.Future

class ZHttp4sServerStubTest
    extends ServerStubInterpreterTest[
      RIO[Clock with Blocking, *],
      ZioStreams with WebSockets,
      Http4sServerOptions[RIO[Clock with Blocking, *], RIO[Clock with Blocking, *]]
    ] {
  override def customInterceptors
      : CustomInterceptors[RIO[Clock with Blocking, *], Http4sServerOptions[RIO[Clock with Blocking, *], RIO[Clock with Blocking, *]]] =
    Http4sServerOptions
      .customInterceptors[RIO[Clock with Blocking, *], RIO[Clock with Blocking, *]]

  override def monad: MonadError[RIO[Clock with Blocking, *]] = new CatsMonadError[RIO[Clock with Blocking, *]]

  override def asFuture[A]: RIO[Clock with Blocking, A] => Future[A] = rio => Runtime.default.unsafeRunToFuture(rio)
}
