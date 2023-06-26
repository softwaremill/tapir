package sttp.tapir.server.netty.internal

import com.typesafe.scalalogging.Logger

object NettyDefaults {
  def debugLog(log: Logger, msg: String, exOpt: Option[Throwable]): Unit =
    exOpt match {
      case None     => log.debug(msg)
      case Some(ex) => log.debug(s"$msg; exception: {}", ex)
    }
}
