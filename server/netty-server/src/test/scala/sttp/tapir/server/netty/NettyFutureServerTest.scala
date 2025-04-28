package sttp.tapir.server.netty

import cats.effect.{IO, Resource}
import cats.syntax.all._
import io.netty.channel.nio.NioEventLoopGroup
import org.scalatest.EitherValues
import sttp.monad.FutureMonad
import sttp.tapir._
import sttp.tapir.server.netty.internal.FutureUtil.nettyFutureToScala
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}
import sttp.client4._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext

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
              new NettyFutureRequestTimeoutTests(eventLoopGroup, backend).tests() ++
              additionalTests(backend)

          (tests, eventLoopGroup)
        }) { case (_, eventLoopGroup) =>
          IO.fromFuture(IO.delay(nettyFutureToScala(eventLoopGroup.shutdownGracefully()): Future[_])).void
        }
        .map { case (tests, _) => tests }
    }

  def additionalTests(backend: Backend[IO])(implicit ec: ExecutionContext): List[Test] = List(
    Test("a large number of requests ignoring the request body shouldn't cause leaks") {
      import cats.effect.unsafe.implicits.global

      val ep = endpoint.post.in(query[Int]("x")).in(stringBody).out(stringBody)
      val sep = ep.serverLogic[Future] { case (a, b) => Future.successful(Right(s"$a $b")) }

      val bind = IO.fromFuture(IO.delay(NettyFutureServer()(ec).addEndpoints(List(sep)).start()))
      Resource
        .make(bind)(server => IO.fromFuture(IO.delay(server.stop())))
        .map(_.port)
        .use { port =>
          (0 until 10000).toList
            .traverse_ { i =>
              basicRequest.response(asStringAlways).body("abcde").post(uri"http://localhost:$port?x=abc").send(backend)
            }
            .map(_ => succeed)
        }
        .unsafeToFuture()
    }
  )
}
