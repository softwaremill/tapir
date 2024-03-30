package sttp.tapir.server.netty.internal

import org.slf4j.Logger

object NettyDefaults {
  def debugLog(log: Logger, msg: String, exOpt: Option[Throwable]): Unit =
    if (log.isDebugEnabled) {
      exOpt match {
        case None     => log.debug(msg)
        case Some(ex) => log.debug(msg, ex)
      }
    }
}
