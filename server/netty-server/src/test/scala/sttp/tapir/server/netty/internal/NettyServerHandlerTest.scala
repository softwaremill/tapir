package sttp.tapir.server.netty.internal

import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.group.DefaultChannelGroup
import io.netty.handler.codec.http.{DefaultFullHttpRequest, FullHttpResponse, HttpHeaderNames, HttpMethod, HttpVersion}
import io.netty.util.concurrent.{DefaultEventExecutorGroup, GlobalEventExecutor}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import sttp.monad.FutureMonad
import sttp.tapir.server.netty.{NettyConfig, Route}

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class NettyServerHandlerTest extends AnyFreeSpec with Matchers {

  "fails, if it doesn't run on the channel's event loop" in {
    val executorGroup = new DefaultEventExecutorGroup(1)
    val channel = new EmbeddedChannel()
    try {
      channel.pipeline().addLast(executorGroup, serverHandler(new AtomicBoolean(false)))
      val _ = executorGroup.submit(new Runnable { override def run(): Unit = () }).sync() // handlerAdded has run
      Try(channel.checkException()).failed.get.getCause shouldBe an[IllegalStateException]
    } finally {
      channel.finishAndReleaseAll()
      val _ = executorGroup.shutdownGracefully()
    }
  }

  "responds to a request with a 503 and closes the connection, if the server is shutting down" in {
    val isShuttingDown = new AtomicBoolean(false)
    val channel = new EmbeddedChannel(serverHandler(isShuttingDown))
    try {
      isShuttingDown.set(true)
      val _ = channel.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"))
      val response = channel.readOutbound[FullHttpResponse]()
      try {
        (response.status().code(), response.headers().get(HttpHeaderNames.CONNECTION), channel.isOpen) shouldBe ((503, "close", false))
      } finally { val _ = response.release() }
    } finally { val _ = channel.finishAndReleaseAll() }
  }

  "closes a connection accepted when the server is shutting down" in {
    val channel = new EmbeddedChannel(serverHandler(new AtomicBoolean(true)))
    try channel.isOpen shouldBe false
    finally { val _ = channel.finishAndReleaseAll() }
  }

  private def serverHandler(isShuttingDown: AtomicBoolean): NettyServerHandler[Future] = {
    implicit val ec: ExecutionContext = ExecutionContext.global
    implicit val m: FutureMonad = new FutureMonad()
    new NettyServerHandler[Future](
      Route.empty,
      block => (block(), () => Future.unit),
      new DefaultChannelGroup(GlobalEventExecutor.INSTANCE),
      isShuttingDown,
      NettyConfig.default
    )
  }
}
