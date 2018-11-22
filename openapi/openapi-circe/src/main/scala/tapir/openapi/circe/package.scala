package tapir.openapi

import io.circe.{Encoder, Json}
import io.circe.syntax._
import io.circe.parser._
import io.circe.magnolia.derivation.encoder.semiauto._
import tapir.openapi.OpenAPI.ReferenceOr

package object circe extends Encoders

trait Encoders {
  // note: these are strict val-s, order matters!

  implicit def encoderReferenceOr[T: Encoder]: Encoder[ReferenceOr[T]] = {
    case Left(Reference(ref)) => Json.obj(("$ref", Json.fromString(ref)))
    case Right(t)             => implicitly[Encoder[T]].apply(t)
  }

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
  implicit val encoderResponseMap: Encoder[Map[ResponsesKey, ReferenceOr[Response]]] =
    (responses: Map[ResponsesKey, ReferenceOr[Response]]) => {
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
  implicit val encoderOpenAPI: Encoder[OpenAPI] = deriveMagnoliaEncoder[OpenAPI]
}
