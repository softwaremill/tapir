package sttp.tapir.server.model

/** The request body ended before the number of bytes declared in `Content-Length` was received, e.g. because the connection was closed. */
case class IncompleteRequestBodyException(bytesReceived: Long, bytesDeclared: Long)
    extends Exception(s"Connection closed with a partially received body: $bytesReceived out of $bytesDeclared bytes received")
