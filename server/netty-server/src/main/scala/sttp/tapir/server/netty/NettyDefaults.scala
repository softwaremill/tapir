package sttp.tapir.server.netty

import com.typesafe.scalalogging.Logger

object NettyDefaults {
  val DefaultHost = "localhost"
  val DefaultPort = 8080

  def debugLog(log: Logger, msg: String, exOpt: Option[Throwable]): Unit =
    exOpt match {
      case None     => log.debug(msg)
      case Some(ex) => log.debug(s"$msg; exception: {}", ex)
    }
}
