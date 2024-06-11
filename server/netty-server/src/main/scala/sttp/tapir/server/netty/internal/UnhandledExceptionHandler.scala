package sttp.tapir.server.netty.internal

import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.util.internal.logging.InternalLoggerFactory

private[internal] class UnhandledExceptionHandler extends ChannelInboundHandlerAdapter {
  private lazy val logger = InternalLoggerFactory.getInstance(getClass)

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    cause match {
      case ex =>
        logger.warn("Unhandled exception", ex)
    }
    val _ = ctx.close()
  }
}
