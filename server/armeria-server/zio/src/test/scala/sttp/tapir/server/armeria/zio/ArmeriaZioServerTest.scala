package sttp.tapir.server.armeria.zio

import cats.effect.{IO, Resource}
import sttp.capabilities.zio.ZioStreams
import sttp.monad.MonadError
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}
import sttp.tapir.ztapir.RIOMonadError
import zio.Task
import zio.stream.ZSink

class ArmeriaZioServerTest extends TestSuite {

  override def tests: Resource[IO, List[Test]] = backendResource.map { backend =>

    implicit val monadError: MonadError[Task] = new RIOMonadError

    val interpreter = new ArmeriaZioTestServerInterpreter()
    val createServerTest = new DefaultCreateServerTest(backend, interpreter)
    def drainZStream(zStream: ZioStreams.BinaryStream): Task[Unit] =
      zStream.run(ZSink.drain)

    new AllServerTests(createServerTest, interpreter, backend, basic = false, options = false, maxContentLength = false, multipart = false)
      .tests() ++
      new ServerMultipartTests(createServerTest, chunkingSupport = false)
        .tests() ++ // chunking disabled, Armeria rejects content-length with transfer-encoding
      new ServerBasicTests(createServerTest, interpreter, supportsUrlEncodedPathSegments = false, maxContentLength = false).tests() ++
      new ServerStreamingTests(createServerTest).tests(ZioStreams)(drainZStream)
  }
}
