package sttp.tapir.server.netty.loom

import cats.effect.{IO, Resource}
import io.netty.channel.nio.NioEventLoopGroup
import org.scalatest.EitherValues
import sttp.tapir.server.netty.internal.FutureUtil.nettyFutureToScala
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}
import ox.*
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.BeforeAndAfterAll
import sttp.client3.HttpClientSyncBackend
import cats.effect.unsafe.implicits.global
import org.scalactic.source.Position
import org.scalatest.funsuite.AnyFunSuite
import ox.channels.Source
import scala.concurrent.Await
import weaver.SimpleIOSuite

class NettyIdServerTest extends IOSuite {

  def testNameFilter: Option[String] = None // define to run a single test (temporarily for debugging)
  scoped {
    val backend = useInScope(backendResource.allocated.unsafeRunSync()) { case (_, release) => release.unsafeRunSync() }._1
    val _ = useInScope(())(_ => println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>> Closing resources in scope"))
    val eventLoopGroup = new NioEventLoopGroup()

    val interpreter = new NettyIdTestServerInterpreter(eventLoopGroup)
    val createServerTest = new DefaultCreateServerTest(backend, interpreter)
    val sleeper: Sleeper[Id] = (duration: FiniteDuration) => Thread.sleep(duration.toMillis)

    val tests =
      new AllServerTests(createServerTest, interpreter, backend, staticContent = false, multipart = false)
        .tests() ++
        new ServerGracefulShutdownTests(createServerTest, sleeper).tests() ++
        new ServerWebSocketTests(createServerTest, OxStreams, autoPing = true, failingPipe = true, handlePong = true) {
          override def functionToPipe[A, B](f: A => B): OxStreams.Pipe[A, B] = ox ?=> in => in.map(f)
          override def emptyPipe[A, B]: OxStreams.Pipe[A, B] = ox ?=> _ => Source.empty
        }.tests()

    tests.foreach { t =>
      if (testNameFilter.forall(filter => t.name.contains(filter))) {
        implicit val pos: Position = t.pos

        test(t.name)(Await.result(t.f(), ))
      }
    }
    println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> ending constructor scope")
  }
}
