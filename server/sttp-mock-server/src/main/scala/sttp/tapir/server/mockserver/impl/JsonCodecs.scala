package sttp.tapir.server.mockserver.impl

import io.circe.generic.codec.DerivedAsObjectCodec
import io.circe.{Codec, CursorOp, Decoder, DecodingFailure, Encoder, Json, JsonObject}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax._
import shapeless.Lazy
import sttp.model.{MediaType, Method, StatusCode, Uri}
import sttp.tapir.server.mockserver.ExpectationBodyDefinition.JsonMatchType
import sttp.tapir.server.mockserver.{
  CreateExpectationRequest,
  Expectation,
  ExpectationBodyDefinition,
  ExpectationRequestDefinition,
  ExpectationResponseDefinition,
  ExpectationTimeToLive,
  ExpectationTimes,
  VerificationTimesDefinition,
  VerifyExpectationRequest
}

private[mockserver] object JsonCodecs {

  private implicit val methodEncoder: Encoder[Method] = Encoder[String].contramap[Method](_.toString)
  private implicit val uriEncoder: Encoder[Uri] = Encoder[String].contramap[Uri](_.toString)
  private implicit val statusCodeEncoder: Encoder[StatusCode] = Encoder[Int].contramap[StatusCode](_.code)
  private implicit val mediaTypeEncoder: Encoder[MediaType] = Encoder[String].contramap[MediaType](_.toString)

  private implicit val methodDecoder: Decoder[Method] = Decoder[String].emap(Method.safeApply)
  private implicit val uriDecoder: Decoder[Uri] = Decoder[String].emap(Uri.parse)
  private implicit val statusCodeDecoder: Decoder[StatusCode] = Decoder[Int].map(StatusCode(_))
  private implicit val mediaTypeDecoder: Decoder[MediaType] = Decoder[String].emap(MediaType.parse)

  private implicit val jsonMatchTypeEncoder: Encoder[JsonMatchType] = Encoder[String].contramap[JsonMatchType](_.entryName)

  private implicit val jsonMatchTypeDecoder: Decoder[JsonMatchType] = Decoder[String].emap[JsonMatchType] {
    case JsonMatchType.Strict.entryName             => Right(JsonMatchType.Strict)
    case JsonMatchType.OnlyMatchingFields.entryName => Right(JsonMatchType.OnlyMatchingFields)
    case other                                      => Left(s"Unexpected json match type: $other")
  }

  private implicit val plainBodyDefnEncoder: Encoder.AsObject[ExpectationBodyDefinition.PlainBodyDefinition] =
    deriveEncoder[ExpectationBodyDefinition.PlainBodyDefinition].mapJsonObject(_.add("type", ExpectationBodyDefinition.PlainType.asJson))

  private implicit val jsonBodyDefnEncoder: Encoder.AsObject[ExpectationBodyDefinition.JsonBodyDefinition] =
    deriveEncoder[ExpectationBodyDefinition.JsonBodyDefinition].mapJsonObject(
      _.add("type", ExpectationBodyDefinition.JsonType.asJson)
    )

  private implicit val expectationBodyDefinitionEncoder: Encoder[ExpectationBodyDefinition] =
    Encoder[Json].contramap[ExpectationBodyDefinition] {
      case plainDefn: ExpectationBodyDefinition.PlainBodyDefinition => plainBodyDefnEncoder(plainDefn)
      case jsonDefn: ExpectationBodyDefinition.JsonBodyDefinition   => jsonBodyDefnEncoder(jsonDefn)
      case rawJson: ExpectationBodyDefinition.RawJson               => rawJson.underlying.asJson
    }

  private implicit val plainBodyDefnDecoder: Decoder[ExpectationBodyDefinition.PlainBodyDefinition] =
    deriveDecoder[ExpectationBodyDefinition.PlainBodyDefinition]

  private implicit val jsonBodyDefnDecoder: Decoder[ExpectationBodyDefinition.JsonBodyDefinition] =
    deriveDecoder[ExpectationBodyDefinition.JsonBodyDefinition]

  private implicit val expectationBodyDefinitionDecoder: Decoder[ExpectationBodyDefinition] = {
    Decoder[JsonObject]
      .emapTry { json =>
        json("type")
          .flatMap(_.asString)
          .map {
            case ExpectationBodyDefinition.PlainType => plainBodyDefnDecoder.decodeJson(json.asJson)
            case ExpectationBodyDefinition.JsonType  => jsonBodyDefnDecoder.decodeJson(json.asJson)
            case other =>
              Left(
                DecodingFailure(
                  message = s"Unexpected body type: `$other`, expected one of ${ExpectationBodyDefinition.KnownTypesString}",
                  ops = List(CursorOp.DownField("type"))
                )
              )
          }
          .getOrElse {
            Right(ExpectationBodyDefinition.RawJson(json))
          }
          .toTry
      }
  }

  private def deriveCodecDropNull[A](implicit codec: Lazy[DerivedAsObjectCodec[A]]): Codec[A] =
    Codec.from(codec.value, codec.value.mapJson(_.dropNullValues))

  private implicit val expectationRequestDefinitionCodec: Codec[ExpectationRequestDefinition] =
    deriveCodecDropNull[ExpectationRequestDefinition]
  private implicit val expectationResponseDefinitionCodec: Codec[ExpectationResponseDefinition] =
    deriveCodecDropNull[ExpectationResponseDefinition]
  private implicit val expectationTimesCodec: Codec[ExpectationTimes] = deriveCodecDropNull[ExpectationTimes]
  private implicit val expectationTimeToLiveCodec: Codec[ExpectationTimeToLive] = deriveCodecDropNull[ExpectationTimeToLive]
  private implicit val verificationTimesDefinitionCodec: Codec[VerificationTimesDefinition] =
    deriveCodecDropNull[VerificationTimesDefinition]

  implicit val createExpectationRequestEncoder: Encoder[CreateExpectationRequest] = deriveEncoder[CreateExpectationRequest]
  implicit val verifyExpectationRequestEncoder: Encoder[VerifyExpectationRequest] = deriveEncoder[VerifyExpectationRequest]
  implicit val expectationDecoder: Decoder[Expectation] = deriveDecoder[Expectation]
}
