package tapir.openapi

import io.circe.magnolia.derivation.encoder.semiauto._
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

  implicit val encoderOAuthFlow: Encoder[OAuthFlow] = deriveMagnoliaEncoder[OAuthFlow]
  implicit val encoderOAuthFlows: Encoder[OAuthFlows] = deriveMagnoliaEncoder[OAuthFlows]
  implicit val encoderSecurityScheme: Encoder[SecurityScheme] = deriveMagnoliaEncoder[SecurityScheme]
  implicit val encoderExampleValue: Encoder[ExampleValue] = { ev: ExampleValue =>
    parse(ev.value).toOption.getOrElse(Json.fromString(ev.value))
  }
  implicit val encoderSchemaFormat: Encoder[SchemaFormat.SchemaFormat] = Encoder.enumEncoder(SchemaFormat)
  implicit val encoderSchemaType: Encoder[SchemaType.SchemaType] = Encoder.enumEncoder(SchemaType)
  implicit val encoderSchema: Encoder[Schema] = deriveMagnoliaEncoder[Schema]
  implicit val encoderReference: Encoder[Reference] = deriveMagnoliaEncoder[Reference]
  implicit val encoderHeader: Encoder[Header] = deriveMagnoliaEncoder[Header]
  implicit val encoderExample: Encoder[Example] = deriveMagnoliaEncoder[Example]
  implicit val encoderResponse: Encoder[Response] = deriveMagnoliaEncoder[Response]
  implicit val encoderEncoding: Encoder[Encoding] = deriveMagnoliaEncoder[Encoding]
  implicit val encoderMediaType: Encoder[MediaType] = deriveMagnoliaEncoder[MediaType]
  implicit val encoderRequestBody: Encoder[RequestBody] = deriveMagnoliaEncoder[RequestBody]
  implicit val encoderParameterStyle: Encoder[ParameterStyle.ParameterStyle] = Encoder.enumEncoder(ParameterStyle)
  implicit val encoderParameterIn: Encoder[ParameterIn.ParameterIn] = Encoder.enumEncoder(ParameterIn)
  implicit val encoderParameter: Encoder[Parameter] = deriveMagnoliaEncoder[Parameter]
  implicit val encoderResponseMap: Encoder[ListMap[ResponsesKey, ReferenceOr[Response]]] =
    (responses: ListMap[ResponsesKey, ReferenceOr[Response]]) => {
      val fields = responses.map {
        case (ResponsesDefaultKey, r)    => ("default", r.asJson)
        case (ResponsesCodeKey(code), r) => (code.toString, r.asJson)
      }

      Json.obj(fields.toSeq: _*)
    }
  implicit val encoderOperation: Encoder[Operation] = deriveMagnoliaEncoder[Operation]
  implicit val encoderPathItem: Encoder[PathItem] = deriveMagnoliaEncoder[PathItem]
  implicit val encoderComponents: Encoder[Components] = deriveMagnoliaEncoder[Components]
  implicit val encoderServer: Encoder[Server] = deriveMagnoliaEncoder[Server]
  implicit val encoderInfo: Encoder[Info] = deriveMagnoliaEncoder[Info]
  implicit val encoderContact: Encoder[Contact] = deriveMagnoliaEncoder[Contact]
  implicit val encoderLicense: Encoder[License] = deriveMagnoliaEncoder[License]
  implicit val encoderOpenAPI: Encoder[OpenAPI] = deriveMagnoliaEncoder[OpenAPI]
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
