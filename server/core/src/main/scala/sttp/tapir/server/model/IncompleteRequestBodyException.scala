package sttp.tapir.server.model

case class IncompleteRequestBodyException(bytesReceived: Long, bytesDeclared: Long) extends Exception {
  override def getMessage(): String = s"Connection closed with partially received body. $bytesReceived out of $bytesDeclared received."
}
