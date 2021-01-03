package sttp.tapir.asyncapi

import io.circe.generic.semiauto._
import io.circe.parser._
import io.circe.syntax._
import io.circe.{Encoder, Json}
import sttp.tapir.apispec.{
  Discriminator,
  ExampleMultipleValue,
  ExampleSingleValue,
  ExampleValue,
  ExternalDocumentation,
  OAuthFlow,
  OAuthFlows,
  Reference,
  ReferenceOr,
  Schema,
  SchemaType,
  SecurityScheme,
  Tag
}

import scala.collection.immutable.ListMap

package object circe extends TapirAsyncAPICirceEncoders

trait TapirAsyncAPICirceEncoders {
  // note: these are strict val-s, order matters!

  implicit def encoderReferenceOr[T: Encoder]: Encoder[ReferenceOr[T]] = {
    case Left(Reference(ref)) => Json.obj(("$ref", Json.fromString(ref)))
    case Right(t)             => implicitly[Encoder[T]].apply(t)
  }

  implicit val encoderOAuthFlow: Encoder[OAuthFlow] = deriveEncoder[OAuthFlow]
  implicit val encoderOAuthFlows: Encoder[OAuthFlows] = deriveEncoder[OAuthFlows]
  implicit val encoderSecurityScheme: Encoder[SecurityScheme] = deriveEncoder[SecurityScheme]
  implicit val encoderExampleValue: Encoder[ExampleValue] = {
    case ExampleSingleValue(value)    => parse(value).getOrElse(Json.fromString(value))
    case ExampleMultipleValue(values) => Json.arr(values.map(v => parse(v).getOrElse(Json.fromString(v))): _*)
  }
  implicit val encoderSchemaType: Encoder[SchemaType.SchemaType] = Encoder.encodeEnumeration(SchemaType)
  implicit val encoderSchema: Encoder[Schema] = deriveEncoder[Schema]
  implicit val encoderReference: Encoder[Reference] = deriveEncoder[Reference]
  implicit val encoderDiscriminator: Encoder[Discriminator] = deriveEncoder[Discriminator]
  implicit val encoderExternalDocumentation: Encoder[ExternalDocumentation] = deriveEncoder[ExternalDocumentation]
  implicit val encoderTag: Encoder[Tag] = deriveEncoder[Tag]

  implicit val encoderAnyValue: Encoder[AnyValue] = (av: AnyValue) => {
    parse(av.value).getOrElse(Json.fromString(av.value))
  }
  implicit val encoderCorrelationId: Encoder[CorrelationId] = deriveEncoder[CorrelationId]
  implicit val encoderParameter: Encoder[Parameter] = deriveEncoder[Parameter]

  implicit val encoderMessageBinding: Encoder[List[MessageBinding]] = {
    implicit val encoderHttpMessageBinding: Encoder[HttpMessageBinding] = deriveEncoder[HttpMessageBinding]
    implicit val encoderWebSocketMessageBinding: Encoder[WebSocketMessageBinding] = deriveEncoder[WebSocketMessageBinding]
    implicit val encoderKafkaMessageBinding: Encoder[KafkaMessageBinding] = deriveEncoder[KafkaMessageBinding]
    (a: List[MessageBinding]) =>
      nullIfEmpty(a)(
        Json.obj(
          a.map {
            case v: HttpMessageBinding      => "http" -> v.asJson
            case v: WebSocketMessageBinding => "ws" -> v.asJson
            case v: KafkaMessageBinding     => "kafka" -> v.asJson
          }: _*
        )
      )
  }

  implicit val encoderOperationBinding: Encoder[List[OperationBinding]] = {
    implicit val encoderHttpOperationBinding: Encoder[HttpOperationBinding] = deriveEncoder[HttpOperationBinding]
    implicit val encoderWebSocketOperationBinding: Encoder[WebSocketOperationBinding] = deriveEncoder[WebSocketOperationBinding]
    implicit val encoderKafkaOperationBinding: Encoder[KafkaOperationBinding] = deriveEncoder[KafkaOperationBinding]
    (a: List[OperationBinding]) =>
      nullIfEmpty(a)(
        Json.obj(
          a.map {
            case v: HttpOperationBinding      => "http" -> v.asJson
            case v: WebSocketOperationBinding => "ws" -> v.asJson
            case v: KafkaOperationBinding     => "kafka" -> v.asJson
          }: _*
        )
      )
  }

  implicit val encoderChannelBinding: Encoder[List[ChannelBinding]] = {
    implicit val encoderHttpChannelBinding: Encoder[HttpChannelBinding] = deriveEncoder[HttpChannelBinding]
    implicit val encoderWebSocketChannelBinding: Encoder[WebSocketChannelBinding] = deriveEncoder[WebSocketChannelBinding]
    implicit val encoderKafkaChannelBinding: Encoder[KafkaChannelBinding] = deriveEncoder[KafkaChannelBinding]
    (a: List[ChannelBinding]) =>
      nullIfEmpty(a)(
        Json.obj(
          a.map {
            case v: HttpChannelBinding      => "http" -> v.asJson
            case v: WebSocketChannelBinding => "ws" -> v.asJson
            case v: KafkaChannelBinding     => "kafka" -> v.asJson
          }: _*
        )
      )
  }

  implicit val encoderServerBinding: Encoder[List[ServerBinding]] = {
    implicit val encoderHttpServerBinding: Encoder[HttpServerBinding] = deriveEncoder[HttpServerBinding]
    implicit val encoderWebSocketServerBinding: Encoder[WebSocketServerBinding] = deriveEncoder[WebSocketServerBinding]
    implicit val encoderKafkaServerBinding: Encoder[KafkaServerBinding] = deriveEncoder[KafkaServerBinding]
    (a: List[ServerBinding]) =>
      nullIfEmpty(a)(
        Json.obj(
          a.map {
            case v: HttpServerBinding      => "http" -> v.asJson
            case v: WebSocketServerBinding => "ws" -> v.asJson
            case v: KafkaServerBinding     => "kafka" -> v.asJson
          }: _*
        )
      )
  }

  private def nullIfEmpty[T](a: List[T])(otherwise: => Json): Json = if (a.isEmpty) Json.Null else otherwise

  implicit val encoderMessagePayload: Encoder[Option[Either[AnyValue, ReferenceOr[Schema]]]] = {
    case None           => Json.Null
    case Some(Left(av)) => encoderAnyValue.apply(av)
    case Some(Right(s)) => encoderReferenceOr[Schema].apply(s)
  }

  implicit val encoderMessageTrait: Encoder[MessageTrait] = deriveEncoder[MessageTrait]
  implicit val encoderSingleMessage: Encoder[SingleMessage] = deriveEncoder[SingleMessage]
  implicit val encoderOneOfMessage: Encoder[OneOfMessage] = deriveEncoder[OneOfMessage]
  implicit val encoderMessage: Encoder[Message] = {
    case s: SingleMessage => encoderSingleMessage.apply(s)
    case o: OneOfMessage  => encoderOneOfMessage.apply(o)
  }

  implicit val encoderOperationTrait: Encoder[OperationTrait] = deriveEncoder[OperationTrait]
  implicit val encoderOperation: Encoder[Operation] = deriveEncoder[Operation]
  implicit val encoderChannelItem: Encoder[ChannelItem] = deriveEncoder[ChannelItem]
  implicit val encoderComponents: Encoder[Components] = deriveEncoder[Components]
  implicit val encoderServerVariable: Encoder[ServerVariable] = deriveEncoder[ServerVariable]
  implicit val encoderServer: Encoder[Server] = deriveEncoder[Server]
  implicit val encoderContact: Encoder[Contact] = deriveEncoder[Contact]
  implicit val encoderLicense: Encoder[License] = deriveEncoder[License]
  implicit val encoderInfo: Encoder[Info] = deriveEncoder[Info]
  implicit val encoderOpenAPI: Encoder[AsyncAPI] = deriveEncoder[AsyncAPI]

  implicit def encodeList[T: Encoder]: Encoder[List[T]] = {
    case Nil        => Json.Null
    case l: List[T] => Json.arr(l.map(i => implicitly[Encoder[T]].apply(i)): _*)
  }
  implicit def encodeListMap[V: Encoder]: Encoder[ListMap[String, V]] = doEncodeListMap(nullWhenEmpty = true)

  private def doEncodeListMap[V: Encoder](nullWhenEmpty: Boolean): Encoder[ListMap[String, V]] = {
    case m: ListMap[String, V] if m.isEmpty && nullWhenEmpty => Json.Null
    case m: ListMap[String, V] =>
      val properties = m.mapValues(v => implicitly[Encoder[V]].apply(v)).toList
      Json.obj(properties: _*)
  }
}
