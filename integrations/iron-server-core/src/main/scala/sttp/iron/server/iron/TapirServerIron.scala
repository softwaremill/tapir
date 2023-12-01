package sttp.iron.server.iron

import sttp.tapir.DecodeResult
import sttp.tapir.DecodeResult.Error
import sttp.tapir.DecodeResult.Error.JsonDecodeException
import sttp.tapir.server.interceptor.DecodeFailureContext
import sttp.tapir.server.interceptor.decodefailure.DefaultDecodeFailureHandler.FailureMessages
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureInterceptor, DefaultDecodeFailureHandler}

trait TapirServerIron {

  case class IronException(errorMessage: String) extends Exception(errorMessage)

  def ironFailureHandler[T[_]] = new DefaultDecodeFailureHandler[T](
    DefaultDecodeFailureHandler.respond,
    failureMessage,
    DefaultDecodeFailureHandler.failureResponse
  )

  def ironDecodeFailureInterceptor[T[_]] = new DecodeFailureInterceptor[T](ironFailureHandler[T])
  
  private def failureDetailMessage(failure: DecodeResult.Failure): Option[String] = failure match {
    case Error(_, JsonDecodeException(_, IronException(errorMessage))) => Some(errorMessage)
    case Error(_, IronException(errorMessage))                         => Some(errorMessage)
    case other                                                         => FailureMessages.failureDetailMessage(other)
  }

  private def failureMessage(ctx: DecodeFailureContext): String = {
    val base = FailureMessages.failureSourceMessage(ctx.failingInput)
    val detail = failureDetailMessage(ctx.failure)
    FailureMessages.combineSourceAndDetail(base, detail)
  }
}
