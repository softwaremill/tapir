package sttp.tapir.server.model

case class ConnectionClosedMidSendException(bytesSend: Long, bytesDeclared: Long) extends Exception {
  override def getMessage(): String = s"Connection closed with partially received body. $bytesSend out of $bytesDeclared received."
}
