package sttp.tapir.model

sealed abstract class StatusCodeRange(val range: Int)
object StatusCodeRange {
  case object Informational extends StatusCodeRange(1)
  case object Success extends StatusCodeRange(2)
  case object Redirect extends StatusCodeRange(3)
  case object ClientError extends StatusCodeRange(4)
  case object ServerError extends StatusCodeRange(5)
}
