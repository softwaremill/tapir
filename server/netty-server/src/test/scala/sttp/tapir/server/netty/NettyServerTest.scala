package sttp.tapir.server.netty

import cats.effect.{IO, Resource}
import io.netty.channel.nio.NioEventLoopGroup
import org.scalatest.EitherValues
import sttp.monad.FutureMonad
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}
import sttp.tapir.server.netty.internal.nettyFutureToScala

class NettyServerTest extends TestSuite with EitherValues {
  override def tests: Resource[IO, List[Test]] =
    backendResource.flatMap { backend =>
      Resource
        .make(IO.delay {
          implicit val m: FutureMonad = new FutureMonad()
          val eventLoopGroup = new NioEventLoopGroup()

          val interpreter = new NettyTestServerInterpreter(eventLoopGroup)
          val createServerTest = new DefaultCreateServerTest(backend, interpreter)

          val tests = new ServerBasicTests(createServerTest, interpreter).tests() ++
            new ServerAuthenticationTests(createServerTest).tests() ++
            new ServerMetricsTest(createServerTest).tests() ++
            new ServerRejectTests(createServerTest, interpreter).tests()

          (tests, eventLoopGroup)
        }) { case (_, eventLoopGroup) =>
          IO.fromFuture(IO.delay(nettyFutureToScala(eventLoopGroup.shutdownGracefully()))).void
        }
        .map { case (tests, _) => tests }
    }
}
