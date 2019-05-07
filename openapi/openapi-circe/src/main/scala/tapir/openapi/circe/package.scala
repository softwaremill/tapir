package tapir.openapi

import io.circe.generic.semiauto._
import io.circe.parser._
import io.circe.syntax._
import io.circe.{Encoder, Json}
import tapir.openapi.OpenAPI.ReferenceOr

import scala.collection.immutable.ListMap

package object circe extends TapirOpenAPICirceEncoders

trait TapirOpenAPICirceEncoders {
  // note: these are strict val-s, order matters!

  implicit def encoderReferenceOr[T: Encoder]: Encoder[ReferenceOr[T]] = {
    case Left(Reference(ref)) => Json.obj(("$ref", Json.fromString(ref)))
    case Right(t)             => implicitly[Encoder[T]].apply(t)
  }

  implicit val encoderOAuthFlow: Encoder[OAuthFlow] = deriveEncoder[OAuthFlow]
  implicit val encoderOAuthFlows: Encoder[OAuthFlows] = deriveEncoder[OAuthFlows]
  implicit val encoderSecurityScheme: Encoder[SecurityScheme] = deriveEncoder[SecurityScheme]
  implicit val encoderExampleValue: Encoder[ExampleValue] = { ev: ExampleValue =>
    parse(ev.value).right.getOrElse(Json.fromString(ev.value))
  }
  implicit val encoderSchemaFormat: Encoder[SchemaFormat.SchemaFormat] = Encoder.enumEncoder(SchemaFormat)
  implicit val encoderSchemaType: Encoder[SchemaType.SchemaType] = Encoder.enumEncoder(SchemaType)
  implicit val encoderSchema: Encoder[Schema] = deriveEncoder[Schema]
  implicit val encoderReference: Encoder[Reference] = deriveEncoder[Reference]
  implicit val encoderHeader: Encoder[Header] = deriveEncoder[Header]
  implicit val encoderExample: Encoder[Example] = deriveEncoder[Example]
  implicit val encoderResponse: Encoder[Response] = deriveEncoder[Response]
  implicit val encoderEncoding: Encoder[Encoding] = deriveEncoder[Encoding]
  implicit val encoderMediaType: Encoder[MediaType] = deriveEncoder[MediaType]
  implicit val encoderRequestBody: Encoder[RequestBody] = deriveEncoder[RequestBody]
  implicit val encoderParameterStyle: Encoder[ParameterStyle.ParameterStyle] = Encoder.enumEncoder(ParameterStyle)
  implicit val encoderParameterIn: Encoder[ParameterIn.ParameterIn] = Encoder.enumEncoder(ParameterIn)
  implicit val encoderParameter: Encoder[Parameter] = deriveEncoder[Parameter]
  implicit val encoderResponseMap: Encoder[ListMap[ResponsesKey, ReferenceOr[Response]]] =
    (responses: ListMap[ResponsesKey, ReferenceOr[Response]]) => {
      val fields = responses.map {
        case (ResponsesDefaultKey, r)    => ("default", r.asJson)
        case (ResponsesCodeKey(code), r) => (code.toString, r.asJson)
      }

      Json.obj(fields.toSeq: _*)
    }
  implicit val encoderOperation: Encoder[Operation] = deriveEncoder[Operation]
  implicit val encoderPathItem: Encoder[PathItem] = deriveEncoder[PathItem]
  implicit val encoderComponents: Encoder[Components] = deriveEncoder[Components]
  implicit val encoderServer: Encoder[Server] = deriveEncoder[Server]
  implicit val encoderExternalDocumentation: Encoder[ExternalDocumentation] = deriveEncoder[ExternalDocumentation]
  implicit val encoderTag: Encoder[Tag] = deriveEncoder[Tag]
  implicit val encoderInfo: Encoder[Info] = deriveEncoder[Info]
  implicit val encoderContact: Encoder[Contact] = deriveEncoder[Contact]
  implicit val encoderLicense: Encoder[License] = deriveEncoder[License]
  implicit val encoderOpenAPI: Encoder[OpenAPI] = deriveEncoder[OpenAPI]
  implicit val encoderDiscriminator: Encoder[Discriminator] = deriveEncoder[Discriminator]
  implicit def encodeList[T: Encoder]: Encoder[List[T]] = {
    case Nil        => Json.Null
    case l: List[T] => Json.arr(l.map(i => implicitly[Encoder[T]].apply(i)): _*)
  }
  implicit def encodeListMap[V: Encoder]: Encoder[ListMap[String, V]] = {
    case m: ListMap[String, V] if m.isEmpty => Json.Null
    case m: ListMap[String, V] =>
      val properties = m.mapValues(v => implicitly[Encoder[V]].apply(v)).toList
      Json.obj(properties: _*)
  }
}
