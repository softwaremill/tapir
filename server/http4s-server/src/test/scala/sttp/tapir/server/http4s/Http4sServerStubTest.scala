package sttp.tapir.server.http4s

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4.testing.BackendStub
import sttp.tapir.integ.cats.effect.CatsMonadError
import sttp.tapir.server.interceptor.CustomiseInterceptors
import sttp.tapir.server.tests.{CreateServerStubTest, ServerStubStreamingTest, ServerStubTest}

import scala.concurrent.Future

object Http4sCreateServerStubTest extends CreateServerStubTest[IO, Http4sServerOptions[IO]] {
  override def customiseInterceptors: CustomiseInterceptors[IO, Http4sServerOptions[IO]] = Http4sServerOptions.customiseInterceptors
  override def stub: BackendStub[IO] = BackendStub[IO](new CatsMonadError[IO])
  override def asFuture[A]: IO[A] => Future[A] = io => io.unsafeToFuture()
}

class Http4sServerStubTest extends ServerStubTest(Http4sCreateServerStubTest)

class Http4sServerStubStreamingTest extends ServerStubStreamingTest(Http4sCreateServerStubTest, Fs2Streams[IO]) {

  /** Must be an instance of streams.BinaryStream */
  override def sampleStream: Any = fs2.Stream.apply[IO, String]("hello")
}
