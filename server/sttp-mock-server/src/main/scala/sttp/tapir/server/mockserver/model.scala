package sttp.tapir.server.mockserver

import io.circe.JsonObject
import sttp.model.{MediaType, Method, StatusCode, Uri}

private[mockserver] final case class CreateExpectationRequest(
    httpRequest: ExpectationRequestDefinition,
    httpResponse: ExpectationResponseDefinition
)

private[mockserver] final case class VerifyExpectationRequest(
    httpRequest: ExpectationRequestDefinition,
    times: VerificationTimesDefinition
)

private[mockserver] final case class VerificationTimesDefinition(atMost: Option[Int], atLeast: Option[Int])

case class Expectation(
    id: String,
    priority: Int,
    httpRequest: ExpectationRequestDefinition,
    httpResponse: ExpectationResponseDefinition,
    times: ExpectationTimes,
    timeToLive: ExpectationTimeToLive
)

case class ExpectationRequestDefinition(
    method: Method,
    path: Uri,
    queryStringParameters: Option[Map[String, List[String]]],
    body: Option[ExpectationBodyDefinition],
    headers: Option[Map[String, List[String]]]
)

sealed trait ExpectationBodyDefinition extends Product with Serializable
object ExpectationBodyDefinition {
  private[mockserver] val PlainType: String = "STRING"
  private[mockserver] val JsonType: String = "JSON"
  private[mockserver] val BinaryType: String = "BINARY"

  private[mockserver] val KnownTypes: List[String] = List(PlainType, JsonType, BinaryType)
  private[mockserver] val KnownTypesString: String = KnownTypes.mkString("[", ", ", "]")

  case class PlainBodyDefinition(string: String, contentType: MediaType) extends ExpectationBodyDefinition
  case class BinaryBodyDefinition(base64Bytes: String, contentType: MediaType) extends ExpectationBodyDefinition
  case class JsonBodyDefinition(json: JsonObject, matchType: JsonMatchType) extends ExpectationBodyDefinition
  // NOTE: for some reasons mock-server just returns the JSON body in httpResponse field...
  case class RawJson(underlying: JsonObject) extends ExpectationBodyDefinition

  sealed trait JsonMatchType {
    def entryName: String
  }
  object JsonMatchType {
    case object Strict extends JsonMatchType {
      override val entryName: String = "STRICT"
    }
    // todo: currently unused
    case object OnlyMatchingFields extends JsonMatchType {
      override val entryName: String = "ONLY_MATCHING_FIELDS"
    }
  }
}

sealed trait ExpectationMatched

case object ExpectationMatched extends ExpectationMatched

case class ExpectationResponseDefinition(
    statusCode: StatusCode,
    body: Option[ExpectationBodyDefinition],
    headers: Option[Map[String, List[String]]]
)

case class ExpectationTimes(unlimited: Boolean, remainingTimes: Option[Int])

case class ExpectationTimeToLive(unlimited: Boolean, timeToLive: Option[Int], timeUnit: Option[String])

class VerificationTimes private (atMost: Option[Int], atLeast: Option[Int]) {
  protected[mockserver] def toDefinition: VerificationTimesDefinition =
    VerificationTimesDefinition(atMost, atLeast)
}

object VerificationTimes {
  val never: VerificationTimes = new VerificationTimes(atMost = Some(0), atLeast = None)

  val exactlyOnce: VerificationTimes = exactly(times = 1)

  val atMostOnce: VerificationTimes = atMost(times = 1)

  val atLeastOnce: VerificationTimes = atLeast(times = 1)

  def apply(atMost: Int, atLeast: Int): VerificationTimes = new VerificationTimes(atMost = Some(atMost), atLeast = Some(atLeast))

  def exactly(times: Int): VerificationTimes = new VerificationTimes(atMost = Some(times), atLeast = Some(times))

  def atMost(times: Int): VerificationTimes = new VerificationTimes(atMost = Some(times), atLeast = None)

  def atLeast(times: Int): VerificationTimes = new VerificationTimes(atMost = None, atLeast = Some(times))
}
