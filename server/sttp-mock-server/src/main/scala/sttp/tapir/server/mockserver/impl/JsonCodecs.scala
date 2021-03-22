package sttp.tapir.server.mockserver.impl

import io.circe.{Codec, Decoder, Encoder}
import io.circe.generic.semiauto.{deriveCodec, deriveDecoder, deriveEncoder}
import sttp.model.{Method, StatusCode, Uri}
import sttp.tapir.server.mockserver.{
  CreateExpectationRequest,
  Expectation,
  ExpectationRequestDefinition,
  ExpectationResponseDefinition,
  ExpectationTimeToLive,
  ExpectationTimes
}

private[mockserver] object JsonCodecs {

  private implicit val methodEncoder: Encoder[Method] = Encoder[String].contramap[Method](_.toString)
  private implicit val uriEncoder: Encoder[Uri] = Encoder[String].contramap[Uri](_.toString)
  private implicit val statusCodeEncoder: Encoder[StatusCode] = Encoder[Int].contramap[StatusCode](_.code)

  private implicit val methodDecoder: Decoder[Method] = Decoder[String].emap(Method.safeApply)
  private implicit val uriDecoder: Decoder[Uri] = Decoder[String].emap(Uri.parse)
  private implicit val statusCodeDecoder: Decoder[StatusCode] = Decoder[Int].map(StatusCode(_))

  private implicit val expectationRequestDefinitionCodec: Codec[ExpectationRequestDefinition] = deriveCodec[ExpectationRequestDefinition]
  private implicit val expectationResponseDefinitionCodec: Codec[ExpectationResponseDefinition] = deriveCodec[ExpectationResponseDefinition]
  private implicit val expectationTimesCodec: Codec[ExpectationTimes] = deriveCodec[ExpectationTimes]
  private implicit val expectationTimeToLiveCodec: Codec[ExpectationTimeToLive] = deriveCodec[ExpectationTimeToLive]

  implicit val createExpectationRequestEncoder: Encoder[CreateExpectationRequest] = deriveEncoder[CreateExpectationRequest]
  implicit val expectationDecoder: Decoder[Expectation] = deriveDecoder[Expectation]
}
