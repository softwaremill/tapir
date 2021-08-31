package sttp.tapir.server.netty

import cats.effect.{IO, Resource}
import io.netty.channel.nio.NioEventLoopGroup
import org.scalatest.EitherValues
import sttp.monad.FutureMonad
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}

class NettyServerTest extends TestSuite with EitherValues {
  override def tests: Resource[IO, List[Test]] =
    backendResource.flatMap { backend =>
      Resource.pure {
        implicit val m: FutureMonad = new FutureMonad()
        val eventLoopGroup = new NioEventLoopGroup()

        val interpreter = new NettyTestServerInterpreter(eventLoopGroup)
        val createServerTest = new DefaultCreateServerTest(backend, interpreter)

        new ServerBasicTests(createServerTest, interpreter).tests() ++
          new ServerAuthenticationTests(createServerTest).tests() ++
          new ServerMetricsTest(createServerTest).tests() ++
          new ServerRejectTests(createServerTest, interpreter).tests()
      }
    }
}
