package sttp.tapir.server.netty

import cats.effect.{IO, Resource}
import io.netty.channel.nio.NioEventLoopGroup
import org.scalatest.EitherValues
import sttp.monad.MonadError
import sttp.tapir.server.netty.internal.{CatsUtil, FutureUtil}
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}

class NettyCatsServerTest extends TestSuite with EitherValues {
  override def tests: Resource[IO, List[Test]] =
    backendResource.flatMap { backend =>
      Resource
        .make {
          implicit val m: MonadError[IO] = new CatsUtil.CatsMonadError[IO]()
          val eventLoopGroup = new NioEventLoopGroup()

          val interpreter = new NettyCatsTestServerInterpreter(eventLoopGroup, dispatcher)
          val createServerTest = new DefaultCreateServerTest(backend, interpreter)

          val tests = new ServerBasicTests(createServerTest, interpreter).tests() ++
            new ServerAuthenticationTests(createServerTest).tests() ++
            new ServerMetricsTest(createServerTest).tests() ++
            new ServerRejectTests(createServerTest, interpreter).tests()

          IO.pure((tests, eventLoopGroup))
        } { case (_, eventLoopGroup) =>
          IO.fromFuture(IO.delay(FutureUtil.nettyFutureToScala(eventLoopGroup.shutdownGracefully()))).void
        }
        .map { case (tests, _) => tests }
    }
}
