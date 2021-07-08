package sttp.tapir.server.interceptor

import sttp.tapir.model.ServerResponse
import sttp.tapir.server.interpreter.DecodeBasicInputsResult

sealed trait ServerInterpreterResult[+B]
object ServerInterpreterResult {
  case class Success[B](response: ServerResponse[B]) extends ServerInterpreterResult[B]
  case class Failure(failures: List[(DecodeFailureContext, DecodeBasicInputsResult.Failure)]) extends ServerInterpreterResult[Nothing]
}
