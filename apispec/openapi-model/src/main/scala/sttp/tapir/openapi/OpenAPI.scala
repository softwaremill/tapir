package sttp.tapir.openapi

import sttp.tapir.apispec.{ExampleValue, ReferenceOr, Schema, SecurityRequirement, SecurityScheme, Tag}

import scala.collection.immutable.ListMap

case class OpenAPI(
    openapi: String = "3.0.1",
    info: Info,
    tags: List[Tag],
    servers: List[Server],
    paths: ListMap[String, PathItem],
    components: Option[Components],
    security: List[SecurityRequirement]
) {
  def addPathItem(path: String, pathItem: PathItem): OpenAPI = {
    val pathItem2 = paths.get(path) match {
      case None           => pathItem
      case Some(existing) => existing.mergeWith(pathItem)
    }

    copy(paths = paths + (path -> pathItem2))
  }

  def servers(s: List[Server]): OpenAPI = copy(servers = s)

  def tags(t: List[Tag]): OpenAPI = copy(tags = t)
}

case class Info(
    title: String,
    version: String,
    description: Option[String] = None,
    termsOfService: Option[String] = None,
    contact: Option[Contact] = None,
    license: Option[License] = None
)

case class Contact(name: Option[String], email: Option[String], url: Option[String])
case class License(name: String, url: Option[String])

case class Server(
    url: String,
    description: Option[String] = None,
    variables: Option[ListMap[String, ServerVariable]] = None
) {
  def description(d: String): Server = copy(description = Some(d))
  def variables(vars: (String, ServerVariable)*): Server = copy(variables = Some(ListMap(vars: _*)))
}

case class ServerVariable(enum: Option[List[String]], default: String, description: Option[String]) {
  require(`enum`.fold(true)(_.contains(default)), "ServerVariable#default must be one of the values in enum if enum is defined")
}

// todo: responses, parameters, examples, requestBodies, headers, links, callbacks
case class Components(
    schemas: ListMap[String, ReferenceOr[Schema]],
    securitySchemes: ListMap[String, ReferenceOr[SecurityScheme]]
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
    servers: List[Server],
    parameters: List[ReferenceOr[Parameter]]
) {
  def mergeWith(other: PathItem): PathItem = {
    PathItem(
      None,
      None,
      get = get.orElse(other.get),
      put = put.orElse(other.put),
      post = post.orElse(other.post),
      delete = delete.orElse(other.delete),
      options = options.orElse(other.options),
      head = head.orElse(other.head),
      patch = patch.orElse(other.patch),
      trace = trace.orElse(other.trace),
      servers = List.empty,
      parameters = List.empty
    )
  }
}

// todo: external docs, callbacks, security
case class Operation(
    tags: List[String],
    summary: Option[String],
    description: Option[String],
    operationId: String,
    parameters: List[ReferenceOr[Parameter]],
    requestBody: Option[ReferenceOr[RequestBody]],
    responses: ListMap[ResponsesKey, ReferenceOr[Response]],
    deprecated: Option[Boolean],
    security: List[SecurityRequirement],
    servers: List[Server]
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
    examples: ListMap[String, ReferenceOr[Example]],
    content: ListMap[String, MediaType]
)

object ParameterIn extends Enumeration {
  type ParameterIn = Value

  val Query: Value = Value("query")
  val Header: Value = Value("header")
  val Path: Value = Value("path")
  val Cookie: Value = Value("cookie")
}

object ParameterStyle extends Enumeration {
  type ParameterStyle = Value

  val Simple: Value = Value("simple")
  val Form: Value = Value("form")
  val Matrix: Value = Value("matrix")
  val Label: Value = Value("label")
  val SpaceDelimited: Value = Value("spaceDelimited")
  val PipeDelimited: Value = Value("pipeDelimited")
  val DeepObject: Value = Value("deepObject")
}

case class RequestBody(description: Option[String], content: ListMap[String, MediaType], required: Option[Boolean])

case class MediaType(
    schema: Option[ReferenceOr[Schema]],
    example: Option[ExampleValue],
    examples: ListMap[String, ReferenceOr[Example]],
    encoding: ListMap[String, Encoding]
)

case class Encoding(
    contentType: Option[String],
    headers: ListMap[String, ReferenceOr[Header]],
    style: Option[ParameterStyle.ParameterStyle],
    explode: Option[Boolean],
    allowReserved: Option[Boolean]
)

sealed trait ResponsesKey
case object ResponsesDefaultKey extends ResponsesKey
case class ResponsesCodeKey(code: Int) extends ResponsesKey

// todo: links
case class Response(description: String, headers: ListMap[String, ReferenceOr[Header]], content: ListMap[String, MediaType])

case class Example(summary: Option[String], description: Option[String], value: Option[ExampleValue], externalValue: Option[String])

case class Header(
    description: Option[String],
    required: Option[Boolean],
    deprecated: Option[Boolean],
    allowEmptyValue: Option[Boolean],
    style: Option[ParameterStyle.ParameterStyle],
    explode: Option[Boolean],
    allowReserved: Option[Boolean],
    schema: Option[ReferenceOr[Schema]],
    example: Option[ExampleValue],
    examples: ListMap[String, ReferenceOr[Example]],
    content: ListMap[String, MediaType]
)
