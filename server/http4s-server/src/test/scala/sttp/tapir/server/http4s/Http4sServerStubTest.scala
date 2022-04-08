package sttp.tapir.server.http4s

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.testing.SttpBackendStub
import sttp.tapir.integ.cats.CatsMonadError
import sttp.tapir.server.interceptor.CustomiseInterceptors
import sttp.tapir.server.tests.{CreateServerStubTest, ServerStubStreamingTest, ServerStubTest}

import scala.concurrent.Future

object Http4sCreateServerStubTest extends CreateServerStubTest[IO, Http4sServerOptions[IO, IO]] {
  override def customiseInterceptors: CustomiseInterceptors[IO, Http4sServerOptions[IO, IO]] = Http4sServerOptions.customiseInterceptors
  override def stub[R]: SttpBackendStub[IO, R] = SttpBackendStub[IO, R](new CatsMonadError[IO])
  override def asFuture[A]: IO[A] => Future[A] = io => io.unsafeToFuture()
}

class Http4sServerStubTest extends ServerStubTest(Http4sCreateServerStubTest)

class Http4sServerStubStreamingTest extends ServerStubStreamingTest(Http4sCreateServerStubTest, Fs2Streams[IO]) {

  /** Must be an instance of streams.BinaryStream */
  override def sampleStream: Any = fs2.Stream.apply[IO, String]("hello")
}
