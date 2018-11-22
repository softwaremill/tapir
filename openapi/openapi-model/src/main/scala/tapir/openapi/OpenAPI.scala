package tapir.openapi

import OpenAPI.ReferenceOr

// todo security, tags, externaldocs
case class OpenAPI(openapi: String = "3.0.1",
                   info: Info,
                   servers: Option[List[Server]],
                   paths: Map[String, PathItem],
                   components: Option[Components])

object OpenAPI {
  type ReferenceOr[T] = Either[Reference, T]
}

// todo: contact, license
case class Info(
    title: String,
    description: Option[String],
    termsOfService: Option[String],
    version: String
)

// todo: variables
case class Server(
    url: String,
    description: Option[String]
)

// todo: responses, parameters, examples, requestBodies, headers, securitySchemas, links, callbacks
case class Components(
    schemas: Option[Map[String, ReferenceOr[Schema]]]
)

// todo: $ref
case class PathItem(
    summary: Option[String],
    description: Option[String],
    get: Option[Operation],
    put: Option[Operation],
    post: Option[Operation],
    delete: Option[Operation],
    options: Option[Operation],
    head: Option[Operation],
    patch: Option[Operation],
    trace: Option[Operation],
    servers: Option[List[Server]],
    parameters: Option[List[ReferenceOr[Parameter]]]
)

// todo: external docs, callbacks, security
case class Operation(
    tags: Option[List[String]],
    summary: Option[String],
    description: Option[String],
    operationId: String,
    parameters: Option[List[ReferenceOr[Parameter]]],
    requestBody: Option[ReferenceOr[RequestBody]],
    responses: Map[ResponsesKey, ReferenceOr[Response]],
    deprecated: Option[Boolean],
    servers: Option[List[Server]]
)

case class Parameter(
    name: String,
    in: ParameterIn.ParameterIn,
    description: Option[String],
    required: Option[Boolean],
    deprecated: Option[Boolean],
    allowEmptyValue: Option[Boolean],
    style: Option[ParameterStyle.ParameterStyle],
    explode: Option[Boolean],
    allowReserved: Option[Boolean],
    schema: ReferenceOr[Schema],
    example: Option[ExampleValue],
    examples: Option[Map[String, ReferenceOr[Example]]],
    content: Option[Map[String, MediaType]]
)

object ParameterIn extends Enumeration {
  type ParameterIn = Value

  val Query = Value("query")
  val Header = Value("header")
  val Path = Value("path")
  val Cookie = Value("cookie")
}

object ParameterStyle extends Enumeration {
  type ParameterStyle = Value

  val Simple = Value("simple")
  val Form = Value("form")
  val Matrix = Value("matrix")
  val Label = Value("label")
  val SpaceDelimited = Value("spaceDelimited")
  val PipeDelimited = Value("pipeDelimited")
  val DeepObject = Value("deepObject")
}

case class RequestBody(description: Option[String], content: Map[String, MediaType], required: Option[Boolean])

case class MediaType(
    schema: Option[ReferenceOr[Schema]],
    example: Option[ExampleValue],
    examples: Option[Map[String, ReferenceOr[Example]]],
    encoding: Option[Map[String, Encoding]]
)

case class Encoding(
    contentType: Option[String],
    headers: Option[Map[String, ReferenceOr[Header]]],
    style: Option[ParameterStyle.ParameterStyle],
    explode: Option[Boolean],
    allowReserved: Option[Boolean]
)

sealed trait ResponsesKey
case object ResponsesDefaultKey extends ResponsesKey
case class ResponsesCodeKey(code: Int) extends ResponsesKey

// todo: links
case class Response(description: String, headers: Option[Map[String, ReferenceOr[Header]]], content: Option[Map[String, MediaType]])

case class Example(summary: Option[String], description: Option[String], value: Option[ExampleValue], externalValue: Option[String])

case class Header(description: Option[String],
                  required: Option[Boolean],
                  deprecated: Option[Boolean],
                  allowEmptyValue: Option[Boolean],
                  style: Option[ParameterStyle.ParameterStyle],
                  explode: Option[Boolean],
                  allowReserved: Option[Boolean],
                  schema: Option[ReferenceOr[Schema]],
                  example: Option[ExampleValue],
                  examples: Option[Map[String, ReferenceOr[Example]]],
                  content: Option[Map[String, MediaType]])

case class Reference($ref: String)

// todo: discriminator, xml, json-schema properties
case class Schema(title: Option[String],
                  required: Option[List[String]],
                  `type`: SchemaType.SchemaType,
                  items: Option[Schema],
                  properties: Option[Map[String, Schema]],
                  description: Option[String],
                  format: Option[SchemaFormat.SchemaFormat],
                  default: Option[ExampleValue],
                  nullable: Option[Boolean],
                  readOnly: Option[Boolean],
                  writeOnly: Option[Boolean],
                  example: Option[ExampleValue],
                  deprecated: Option[Boolean])

object Schema {
  def apply(`type`: SchemaType.SchemaType): Schema = Schema(None, None, `type`, None, None, None, None, None, None, None, None, None, None)
}

object SchemaType extends Enumeration {
  type SchemaType = Value

  val Boolean = Value("boolean")
  val Object = Value("object")
  val Array = Value("array")
  val Number = Value("number")
  val String = Value("string")
  val Integer = Value("integer")
}

object SchemaFormat extends Enumeration {
  type SchemaFormat = Value

  val Int32 = Value("int32")
  val Int64 = Value("int64")
  val Float = Value("float")
  val Double = Value("double")
  val Byte = Value("byte")
  val Binary = Value("binary")
  val Date = Value("date")
  val DateTime = Value("date-time")
  val Password = Value("password")
}

case class ExampleValue(value: String)
