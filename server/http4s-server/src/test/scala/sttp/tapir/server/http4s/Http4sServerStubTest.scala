package sttp.tapir.server.http4s

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.monad.MonadError
import sttp.tapir.integ.cats.CatsMonadError
import sttp.tapir.server.interceptor.CustomInterceptors
import sttp.tapir.server.tests.ServerStubInterpreterTest

import scala.concurrent.Future

class Http4sServerStubTest extends ServerStubInterpreterTest[IO, Fs2Streams[IO] with WebSockets, Http4sServerOptions[IO, IO]] {
  override def customInterceptors: CustomInterceptors[IO, Http4sServerOptions[IO, IO]] = Http4sServerOptions.customInterceptors
  override def monad: MonadError[IO] = new CatsMonadError[IO]
  override def asFuture[A]: IO[A] => Future[A] = io => io.unsafeToFuture()
}
