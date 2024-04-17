package sttp.tapir.server.netty.loom

import cats.effect.{IO, Resource}
import io.netty.channel.nio.NioEventLoopGroup
import org.scalatest.EitherValues
import sttp.tapir.server.netty.internal.FutureUtil.nettyFutureToScala
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class NettySyncServerTest extends TestSuite with EitherValues {
  override def tests: Resource[IO, List[Test]] =
    backendResource.flatMap { backend =>
      Resource
        .make(IO.delay {
          val eventLoopGroup = new NioEventLoopGroup()

          val interpreter = new NettySyncTestServerInterpreter(eventLoopGroup)
          val createServerTest = new DefaultCreateServerTest(backend, interpreter)
          val sleeper: Sleeper[Id] = (duration: FiniteDuration) => Thread.sleep(duration.toMillis)

          val tests =
            new AllServerTests(createServerTest, interpreter, backend, staticContent = false, multipart = false)
              .tests() ++
              new ServerGracefulShutdownTests(createServerTest, sleeper).tests()

          (tests, eventLoopGroup)
        }) { case (_, eventLoopGroup) =>
          IO.fromFuture(IO.delay(nettyFutureToScala(eventLoopGroup.shutdownGracefully()): Future[_])).void
        }
        .map { case (tests, _) => tests }
    }
}
