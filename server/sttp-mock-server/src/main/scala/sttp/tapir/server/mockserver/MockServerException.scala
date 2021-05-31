package sttp.tapir.server.mockserver

import sttp.model.StatusCode

sealed trait MockServerException extends RuntimeException

object MockServerException {
  case class IncorrectRequestFormat(message: String) extends MockServerException {
    override def getMessage: String =
      s"Failed to interact with mock server: incorrect request format $message"
  }

  case class InvalidExpectation(message: String) extends MockServerException {
    override def getMessage: String =
      s"Failed to interact with mock server: invalid expectation $message"
  }

  case class UnexpectedError(statusCode: StatusCode, message: String) extends MockServerException {
    override def getMessage: String =
      s"Failed to interact with mock server: unexpected error status=${statusCode.code} $message"
  }
}
