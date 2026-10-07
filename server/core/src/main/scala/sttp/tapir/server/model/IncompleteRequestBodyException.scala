package sttp.tapir.server.model

case class IncompleteRequestBodyException(bytesReceived: Long, bytesDeclared: Long)
    extends Exception(s"Connection closed with a partially received body: $bytesReceived out of $bytesDeclared bytes received")
