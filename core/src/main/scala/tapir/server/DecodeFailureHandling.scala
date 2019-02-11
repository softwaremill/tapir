package tapir.server
import tapir.{Codec, MediaType, StatusCode}

trait DecodeFailureHandling

object DecodeFailureHandling {
  case object NoMatch extends DecodeFailureHandling
  case class RespondWithResponse[T, M <: MediaType, R](statusCode: StatusCode, body: T, codec: Codec[T, M, R]) extends DecodeFailureHandling

  def noMatch: DecodeFailureHandling = NoMatch
  def response[T](statusCode: StatusCode, body: T)(implicit c: Codec[T, _ <: MediaType, _]): DecodeFailureHandling =
    RespondWithResponse(statusCode, body, c)
}
