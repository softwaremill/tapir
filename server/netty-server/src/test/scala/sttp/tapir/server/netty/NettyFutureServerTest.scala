package sttp.tapir.server.netty

import cats.effect.{IO, Resource}
import io.netty.channel.nio.NioEventLoopGroup
import org.scalatest.EitherValues
import sttp.monad.FutureMonad
import sttp.tapir.server.netty.internal.FutureUtil.nettyFutureToScala
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}

import scala.concurrent.Future

class NettyFutureServerTest extends TestSuite with EitherValues {
  override def tests: Resource[IO, List[Test]] =
    backendResource.flatMap { backend =>
      Resource
        .make(IO.delay {
          implicit val m: FutureMonad = new FutureMonad()
          val eventLoopGroup = new NioEventLoopGroup()

          val interpreter = new NettyFutureTestServerInterpreter(eventLoopGroup)
          val createServerTest = new DefaultCreateServerTest(backend, interpreter)

          val tests =
            new AllServerTests(createServerTest, interpreter, backend, multipart = false).tests() ++
              new ServerGracefulShutdownTests(createServerTest, Sleeper.futureSleeper).tests() ++
              new NettyRequestTimeoutTests(eventLoopGroup, backend).tests()

          (tests, eventLoopGroup)
        }) { case (_, eventLoopGroup) =>
          IO.fromFuture(IO.delay(nettyFutureToScala(eventLoopGroup.shutdownGracefully()): Future[_])).void
        }
        .map { case (tests, _) => tests }
    }
}
