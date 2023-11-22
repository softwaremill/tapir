package sttp.tapir.server.armeria.cats

import cats.effect.{IO, Resource}
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.integ.cats.effect.CatsMonadError
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}

class ArmeriaCatsServerTest extends TestSuite {

  override def tests: Resource[IO, List[Test]] = backendResource.map { backend =>
    implicit val m: CatsMonadError[IO] = new CatsMonadError[IO]

    val interpreter = new ArmeriaCatsTestServerInterpreter(dispatcher)
    val createServerTest = new DefaultCreateServerTest(backend, interpreter)
    def drainFs2(stream: Fs2Streams[IO]#BinaryStream): IO[Unit] =
      stream.compile.drain.void

    new AllServerTests(createServerTest, interpreter, backend, basic = false, options = false).tests() ++
      new ServerBasicTests(createServerTest, interpreter, supportsUrlEncodedPathSegments = false).tests() ++
      new ServerStreamingTests(createServerTest, maxLengthSupported = true).tests(Fs2Streams[IO])(drainFs2)
  }
}
