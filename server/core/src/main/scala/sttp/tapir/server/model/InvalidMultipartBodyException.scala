package sttp.tapir.server.model

case class InvalidMultipartBodyException(message: String, cause: Throwable) extends Exception(message, cause)

object InvalidMultipartBodyException {
  def apply(cause: Throwable): InvalidMultipartBodyException = new InvalidMultipartBodyException(cause.getMessage, cause)
  def apply(message: String): InvalidMultipartBodyException = InvalidMultipartBodyException(message, null)
}
