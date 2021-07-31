package sttp.tapir.server.http4s.ztapir

import cats.effect._
import org.scalatest.OptionValues
import sttp.capabilities.zio.ZioStreams
import sttp.monad.MonadError
import sttp.tapir.integ.cats.CatsMonadError
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}
import zio.RIO
import zio.blocking.Blocking
import zio.clock.Clock
import zio.interop.catz._

class ZHttp4sServerTest extends TestSuite with OptionValues {

  override def tests: Resource[IO, List[Test]] = backendResource.map { backend =>
    implicit val m: MonadError[RIO[Clock with Blocking, *]] = new CatsMonadError[RIO[Clock with Blocking, *]]

    val interpreter = new ZHttp4sTestServerInterpreter()
    val createServerTest = new DefaultCreateServerTest(backend, interpreter)

    new ServerBasicTests(createServerTest, interpreter).tests() ++
      new ServerFileMultipartTests(createServerTest).tests() ++
      new ServerStreamingTests(createServerTest, ZioStreams).tests() ++
      new ServerWebSocketTests(createServerTest, ZioStreams) {
        override def functionToPipe[A, B](f: A => B): streams.Pipe[A, B] = in => in.map(f)
      }.tests() ++
      new ServerAuthenticationTests(createServerTest).tests() ++
      new ServerMetricsTest(createServerTest).tests() ++
      new ServerRejectTests(createServerTest, interpreter).tests()
  }
}
