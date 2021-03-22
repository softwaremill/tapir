package sttp.tapir.server.mockserver

import sttp.model.{Method, StatusCode, Uri}

private[mockserver] final case class CreateExpectationRequest(
    httpRequest: ExpectationRequestDefinition,
    httpResponse: ExpectationResponseDefinition
)

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
    body: Option[String],
    headers: Option[Map[String, List[String]]]
)

case class ExpectationResponseDefinition(body: Option[String], headers: Option[Map[String, List[String]]], statusCode: StatusCode)

case class ExpectationTimes(unlimited: Boolean, remainingTimes: Option[Int])

case class ExpectationTimeToLive(unlimited: Boolean, timeToLive: Option[Int], timeUnit: Option[String])
