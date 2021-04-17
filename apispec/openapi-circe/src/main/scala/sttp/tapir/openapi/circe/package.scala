package sttp.tapir.openapi

import io.circe.generic.encoding.DerivedAsObjectEncoder
import io.circe.generic.semiauto._
import io.circe.parser._
import io.circe.syntax._
import io.circe.{Encoder, Json, JsonObject}
import shapeless.Lazy
import sttp.tapir.apispec.{
  Discriminator,
  ExampleMultipleValue,
  ExampleSingleValue,
  ExampleValue,
  DocsExtensionValue,
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

package object circe extends TapirOpenAPICirceEncoders

trait TapirOpenAPICirceEncoders {
  // note: these are strict val-s, order matters!

  implicit def encoderReferenceOr[T: Encoder]: Encoder[ReferenceOr[T]] = {
    case Left(Reference(ref)) => Json.obj(("$ref", Json.fromString(ref)))
    case Right(t)             => implicitly[Encoder[T]].apply(t)
  }

  implicit val docsExtensionValue: Encoder[DocsExtensionValue] = Encoder.instance(e => parse(e.value).getOrElse(Json.fromString(e.value)))
  implicit val encoderOAuthFlow: Encoder[OAuthFlow] = deriveWithExtensions[OAuthFlow]
  implicit val encoderOAuthFlows: Encoder[OAuthFlows] = deriveWithExtensions[OAuthFlows]
  implicit val encoderSecurityScheme: Encoder[SecurityScheme] = deriveWithExtensions[SecurityScheme]
  implicit val encoderExampleValue: Encoder[ExampleValue] = {
    case ExampleSingleValue(value)    => parse(value).getOrElse(Json.fromString(value))
    case ExampleMultipleValue(values) => Json.arr(values.map(v => parse(v).getOrElse(Json.fromString(v))): _*)
  }
  implicit val encoderSchemaType: Encoder[SchemaType.SchemaType] = Encoder.encodeEnumeration(SchemaType)
  implicit val encoderSchema: Encoder[Schema] = deriveWithExtensions[Schema]
  implicit val encoderReference: Encoder[Reference] = deriveEncoder[Reference]
  implicit val encoderHeader: Encoder[Header] = deriveEncoder[Header]
  implicit val encoderExample: Encoder[Example] = deriveWithExtensions[Example]
  implicit val encoderResponse: Encoder[Response] = deriveWithExtensions[Response]
  implicit val encoderEncoding: Encoder[Encoding] = deriveWithExtensions[Encoding]
  implicit val encoderMediaType: Encoder[MediaType] = deriveWithExtensions[MediaType]
  implicit val encoderRequestBody: Encoder[RequestBody] = deriveWithExtensions[RequestBody]
  implicit val encoderParameterStyle: Encoder[ParameterStyle.ParameterStyle] = Encoder.encodeEnumeration(ParameterStyle)
  implicit val encoderParameterIn: Encoder[ParameterIn.ParameterIn] = Encoder.encodeEnumeration(ParameterIn)
  implicit val encoderParameter: Encoder[Parameter] = deriveWithExtensions[Parameter]
  implicit val encoderResponseMap: Encoder[ListMap[ResponsesKey, ReferenceOr[Response]]] =
    (responses: ListMap[ResponsesKey, ReferenceOr[Response]]) => {
      val fields = responses.map {
        case (ResponsesDefaultKey, r)    => ("default", r.asJson)
        case (ResponsesCodeKey(code), r) => (code.toString, r.asJson)
      }

      Json.obj(fields.toSeq: _*)
    }
  implicit val encoderResponses: Encoder[Responses] = Encoder.instance { resp =>
    val extensions = resp.docsExtensions.asJsonObject
    val respJson = resp.responses.asJson
    respJson.asObject.map(_.deepMerge(extensions).asJson).getOrElse(respJson)
  }
  implicit val encoderOperation: Encoder[Operation] = {
    // this is needed to override the encoding of `security: List[SecurityRequirement]`. An empty security requirement
    // should be represented as an empty object (`{}`), not `null`, which is the default encoding of `ListMap`s.
    implicit def encodeListMap[V: Encoder]: Encoder[ListMap[String, V]] = doEncodeListMap(nullWhenEmpty = false)
    deriveWithExtensions[Operation]
  }
  implicit val encoderPathItem: Encoder[PathItem] = deriveWithExtensions[PathItem]
  implicit val encoderPaths: Encoder[Paths] = Encoder.instance { paths =>
    val extensions = paths.docsExtensions.asJsonObject
    val pathItems = paths.pathItems.asJson
    pathItems.asObject.map(_.deepMerge(extensions).asJson).getOrElse(pathItems)
  }
  implicit val encoderComponents: Encoder[Components] = deriveWithExtensions[Components]
  implicit val encoderServerVariable: Encoder[ServerVariable] = deriveWithExtensions[ServerVariable]
  implicit val encoderServer: Encoder[Server] = deriveWithExtensions[Server]
  implicit val encoderExternalDocumentation: Encoder[ExternalDocumentation] = deriveWithExtensions[ExternalDocumentation]
  implicit val encoderTag: Encoder[Tag] = deriveWithExtensions[Tag]
  implicit val encoderInfo: Encoder[Info] = deriveWithExtensions[Info]
  implicit val encoderContact: Encoder[Contact] = deriveWithExtensions[Contact]
  implicit val encoderLicense: Encoder[License] = deriveWithExtensions[License]
  implicit val encoderOpenAPI: Encoder[OpenAPI] = deriveWithExtensions[OpenAPI].mapJson(_.deepDropNullValues)
  implicit val encoderDiscriminator: Encoder[Discriminator] = deriveEncoder[Discriminator]
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

  /*
      Openapi extensions are arbitrary key-value data that could be added to some of models in specifications, such
      as `OpenAPI` itself, `License`, `Parameter`, etc.

      The key could be any string (that starts with 'x-' by convention) and value is arbitrary Json (string, object,
      array, etc.)

      To be able to encode such arbitrary data and apply it to the final Json it passed through the `docsExtensions` field
      in models and moved (or expanded) to the object level while encoding

      Example:

      ```
      case class License(
         name: String,
         url: Option[String],
         docsExtensions: ListMap[String, ExtensionValue] = ListMap.empty
      )

      val licenseWithExtension = License("hello", None, ListMap("x-foo", ExtensionValue("42"))
      ```

      Applying the transformation below we end up with the following schema in the specification:

      ```
      license:
        name: hello
        x-foo: 42
      ```
   */
  private def expandExtensions(jsonObject: JsonObject): JsonObject = {
    val extensions = jsonObject("docsExtensions")
    val jsonWithoutExt = jsonObject.filterKeys(_ != "docsExtensions")
    extensions.flatMap(_.asObject).map(extObject => extObject.deepMerge(jsonWithoutExt)).getOrElse(jsonWithoutExt)
  }

  private def deriveWithExtensions[A](implicit encode: Lazy[DerivedAsObjectEncoder[A]]) = {
    deriveEncoder[A].mapJsonObject(expandExtensions)
  }
}
