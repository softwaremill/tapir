package sttp.tapir.server.netty.zio

import cats.effect.{IO, Resource}
import io.netty.channel.nio.NioEventLoopGroup
import org.scalatest.EitherValues
import sttp.monad.MonadError
import sttp.tapir.server.netty.internal.FutureUtil
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}
import sttp.tapir.ztapir.RIOMonadError
import zio.Task

class NettyZioServerTest extends TestSuite with EitherValues {
  override def tests: Resource[IO, List[Test]] =
    backendResource.flatMap { backend =>
      Resource
        .make {
          implicit val monadError: MonadError[Task] = new RIOMonadError[Any]
          val eventLoopGroup = new NioEventLoopGroup()

          val interpreter = new NettyZioTestServerInterpreter(eventLoopGroup)
          val createServerTest = new DefaultCreateServerTest(backend, interpreter)

          val tests =
            new AllServerTests(createServerTest, interpreter, backend, staticContent = false, multipart = false).tests() ++
              new ServerMultipartTests(createServerTest, partOtherHeaderSupport = false).tests()

          IO.pure((tests, eventLoopGroup))
        } { case (_, eventLoopGroup) =>
          IO.fromFuture(IO.delay(FutureUtil.nettyFutureToScala(eventLoopGroup.shutdownGracefully()))).void
        }
        .map { case (tests, _) => tests }
    }
}
