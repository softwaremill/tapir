package sttp.tapir.serverless.aws.sam

import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax.EncoderOps
import io.circe.{Encoder, Json}

import scala.collection.immutable.ListMap

object AwsSamTemplateEncoders {
  implicit def encodeListMap[V: Encoder]: Encoder[ListMap[String, V]] = { case m: ListMap[String, V] =>
    val properties = m.view.map { case (k, v) => k -> implicitly[Encoder[V]].apply(v) }.toList
    Json.obj(properties: _*)
  }

  implicit val encoderOutput: Encoder[Output] = deriveEncoder[Output]
  implicit val encoderFunctionHttpApiEventProperties: Encoder[FunctionHttpApiEventProperties] =
    deriveEncoder[FunctionHttpApiEventProperties]
  implicit val encoderFunctionHttpApiEvent: Encoder[FunctionHttpApiEvent] = {
    val encoder = deriveEncoder[FunctionHttpApiEvent]
    e => Json.fromJsonObject(encoder(e).asJson.asObject.get.add("Type", Json.fromString("HttpApi")))
  }

  implicit val encoderCorsConfiguration: Encoder[CorsConfiguration] = deriveEncoder[CorsConfiguration]
  implicit val encoderEnvironmentCodeProperties: Encoder[EnvironmentCodeProperties] = deriveEncoder[EnvironmentCodeProperties]
  implicit val encoderHttpProperties: Encoder[HttpProperties] = deriveEncoder[HttpProperties]
  implicit val encoderFunctionImageProperties: Encoder[FunctionImageProperties] = deriveEncoder[FunctionImageProperties]
  implicit val encoderFunctionCodeProperties: Encoder[FunctionCodeProperties] = deriveEncoder[FunctionCodeProperties]
  implicit val encoderProperties: Encoder[Properties] = {
    case v: HttpProperties          => v.asJson
    case v: FunctionImageProperties => v.asJson
    case v: FunctionCodeProperties  => v.asJson
  }

  implicit val encoderHttpResource: Encoder[HttpResource] = deriveEncoder[HttpResource]
  implicit val encoderFunctionResource: Encoder[FunctionResource] = deriveEncoder[FunctionResource]
  implicit val encoderResource: Encoder[Resource] = {
    case v: HttpResource     => Json.fromJsonObject(v.asJson.asObject.get.add("Type", Json.fromString("AWS::Serverless::HttpApi")))
    case v: FunctionResource => Json.fromJsonObject(v.asJson.asObject.get.add("Type", Json.fromString("AWS::Serverless::Function")))
  }

  implicit val encoderSamTemplate: Encoder[SamTemplate] = deriveEncoder[SamTemplate]
}
