package sttp.tapir.server.netty

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.netty.channel.EventLoopGroup
import org.scalatest.Assertions.succeed
import org.scalatest.matchers.should.Matchers._
import sttp.tapir.server.tests.threadsLeftBy
import sttp.tapir.tests.Test

import scala.concurrent.duration._

/** Tests of stopping a server, shared by all Netty servers.
  *
  * @param eventLoopGroup
  *   an event loop group which outlives the tests
  */
abstract class NettyServerStopTests(eventLoopGroup: EventLoopGroup) {

  /** Starts a server with the given config, and runs `use`, passing it an effect which calls the server's `stop()`. */
  def withServer[A](config: NettyConfig)(use: IO[Unit] => IO[A]): IO[A]

  def tests(): List[Test] = List(
    Test("stopping a server more than once") {
      withServer(NettyConfig.default.randomPort)(stop => stop >> stop).map(_ => succeed).unsafeToFuture()
    },
    Test("stopping a server which uses its own event loop group should use a short quiet period, not Netty's default of 2 seconds") {
      withServer(NettyConfig.default.randomPort)(_.timed)
        .map { case (stopDuration, _) => stopDuration should be < 2.seconds }
        .unsafeToFuture()
    },
    Test("stopping a server which doesn't shut down its event loop group releases the other threads it starts") {
      val config = NettyConfig.default.eventLoopGroup(eventLoopGroup).randomPort.withDontShutdownEventLoopGroupOnClose
      threadsLeftBy("defaultEventExecutor")(withServer(config)(identity))
        .map(_ shouldBe empty)
        .unsafeToFuture()
    }
  )
}
