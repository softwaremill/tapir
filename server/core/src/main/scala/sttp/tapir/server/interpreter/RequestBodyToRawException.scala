package sttp.tapir.server.interpreter

import sttp.tapir.DecodeResult

/** Can be used with RequestBody.toRaw to fail its effect F and pass failures, which are treated as decoding failures that happen before
  * actual decoding of the raw value.
  */
private[tapir] case class RequestBodyToRawException(failure: DecodeResult.Failure) extends Exception
