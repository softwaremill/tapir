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

  implicit val encodeParameter: Encoder[Parameter] = {
    val encoder = deriveEncoder[Parameter]
    v => Json.fromJsonObject(encoder(v).asObject.get.add("Type", Json.fromString("String")))
  }

  implicit val encoderOutput: Encoder[Output] = deriveEncoder[Output]
  implicit val encoderFunctionHttpApiEventProperties: Encoder[FunctionHttpApiEventProperties] =
    deriveEncoder[FunctionHttpApiEventProperties]
  implicit val encoderFunctionHttpApiEvent: Encoder[FunctionHttpApiEvent] = {
    val encoder = deriveEncoder[FunctionHttpApiEvent]
    e => Json.fromJsonObject(encoder(e).asObject.get.add("Type", Json.fromString("HttpApi")))
  }

  implicit val encoderCorsConfiguration: Encoder[CorsConfiguration] = deriveEncoder[CorsConfiguration]
  implicit val encoderEnvironmentCodeProperties: Encoder[EnvironmentCodeProperties] = deriveEncoder[EnvironmentCodeProperties]
  implicit val encoderJwtConfiguration: Encoder[JwtConfiguration] = deriveEncoder[JwtConfiguration]
  implicit val encoderLambdaAuthorizationIdentity: Encoder[LambdaAuthorizationIdentity] = deriveEncoder[LambdaAuthorizationIdentity]
  implicit val encoderAuthorizer: Encoder[Authorizer] = new Encoder[Authorizer] {
    private val oAuth2AuthorizerEncoder: Encoder[OAuth2Authorizer] = deriveEncoder[OAuth2Authorizer]
    private val lambdaAuthorizerEncoder: Encoder[LambdaAuthorizer] = deriveEncoder[LambdaAuthorizer]

    override def apply(a: Authorizer): Json = a match {
      case a: OAuth2Authorizer => oAuth2AuthorizerEncoder(a)
      case a: LambdaAuthorizer => lambdaAuthorizerEncoder(a)
    }
  }

  implicit val encoderHttpApiAuth: Encoder[HttpApiAuth] = deriveEncoder[HttpApiAuth]

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
