package sttp.tapir.openapi

import sttp.tapir.apispec.{ExampleValue, ExtensionValue, ReferenceOr, Schema, SecurityRequirement, SecurityScheme, Tag}

import scala.collection.immutable.ListMap

case class OpenAPI(
    openapi: String = "3.1.0",
    info: Info,
    jsonSchemaDialect: Option[String],
    tags: List[Tag],
    servers: List[Server],
    paths: Paths,
    webhooks: Option[Map[String, PathItem]],
    components: Option[Components],
    security: List[SecurityRequirement],
    extensions: ListMap[String, ExtensionValue] = ListMap.empty
) {
  def addPathItem(path: String, pathItem: PathItem): OpenAPI = {
    val pathItem2 = paths.pathItems.get(path) match {
      case None           => pathItem
      case Some(existing) => existing.mergeWith(pathItem)
    }
    val newPathItems = paths.pathItems + (path -> pathItem2)
    val newPath = paths.copy(pathItems = newPathItems)
    copy(paths = newPath)
  }

  def servers(s: List[Server]): OpenAPI = copy(servers = s)

  def tags(t: List[Tag]): OpenAPI = copy(tags = t)

  def jsonSchemaDialect(d: Option[String]): OpenAPI = copy(jsonSchemaDialect = d)

  def webhooks(wh: Option[Map[String, PathItem]]): OpenAPI = copy(webhooks = wh)
}

case class Info(
    title: String,
    version: String,
    description: Option[String] = None,
    termsOfService: Option[String] = None,
    contact: Option[Contact] = None,
    license: Option[License] = None,
    extensions: ListMap[String, ExtensionValue] = ListMap.empty
)

case class Contact(
    name: Option[String],
    email: Option[String],
    url: Option[String],
    extensions: ListMap[String, ExtensionValue] = ListMap.empty
)
case class License(
    name: String,
    url: Option[String],
    extensions: ListMap[String, ExtensionValue] = ListMap.empty
)

case class Server(
    url: String,
    description: Option[String] = None,
    variables: Option[ListMap[String, ServerVariable]] = None,
    extensions: ListMap[String, ExtensionValue] = ListMap.empty
) {
  def description(d: String): Server = copy(description = Some(d))
  def variables(vars: (String, ServerVariable)*): Server = copy(variables = Some(ListMap(vars: _*)))
}

case class ServerVariable(
    enum: Option[List[String]],
    default: String,
    description: Option[String],
    extensions: ListMap[String, ExtensionValue] = ListMap.empty
) {
  require(`enum`.fold(true)(_.contains(default)), "ServerVariable#default must be one of the values in enum if enum is defined")
}

// todo: responses, parameters, examples, requestBodies, headers, links, callbacks
case class Components(
    schemas: ListMap[String, ReferenceOr[Schema]],
    securitySchemes: ListMap[String, ReferenceOr[SecurityScheme]],
    extensions: ListMap[String, ExtensionValue] = ListMap.empty
)

case class Paths(
    pathItems: ListMap[String, PathItem],
    extensions: ListMap[String, ExtensionValue] = ListMap.empty
)

// todo: $ref
case class PathItem(
    summary: Option[String] = None,
    description: Option[String] = None,
    get: Option[Operation] = None,
    put: Option[Operation] = None,
    post: Option[Operation] = None,
    delete: Option[Operation] = None,
    options: Option[Operation] = None,
    head: Option[Operation] = None,
    patch: Option[Operation] = None,
    trace: Option[Operation] = None,
    servers: List[Server] = List.empty,
    parameters: List[ReferenceOr[Parameter]] = List.empty,
    extensions: ListMap[String, ExtensionValue] = ListMap.empty
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
    tags: List[String] = List.empty,
    summary: Option[String] = None,
    description: Option[String] = None,
    operationId: String,
    parameters: List[ReferenceOr[Parameter]] = List.empty,
    requestBody: Option[ReferenceOr[RequestBody]] = None,
    responses: Responses = Responses(ListMap.empty),
    deprecated: Option[Boolean] = None,
    security: List[SecurityRequirement] = List.empty,
    servers: List[Server] = List.empty,
    extensions: ListMap[String, ExtensionValue] = ListMap.empty
)

case class Parameter(
    name: String,
    in: ParameterIn.ParameterIn,
    description: Option[String] = None,
    required: Option[Boolean] = None,
    deprecated: Option[Boolean] = None,
    allowEmptyValue: Option[Boolean] = None,
    style: Option[ParameterStyle.ParameterStyle] = None,
    explode: Option[Boolean] = None,
    allowReserved: Option[Boolean] = None,
    schema: ReferenceOr[Schema],
    example: Option[ExampleValue] = None,
    examples: ListMap[String, ReferenceOr[Example]] = ListMap.empty,
    content: ListMap[String, MediaType] = ListMap.empty,
    extensions: ListMap[String, ExtensionValue] = ListMap.empty
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

case class RequestBody(
    description: Option[String],
    content: ListMap[String, MediaType],
    required: Option[Boolean],
    extensions: ListMap[String, ExtensionValue] = ListMap.empty
)

case class MediaType(
    schema: Option[ReferenceOr[Schema]] = None,
    example: Option[ExampleValue] = None,
    examples: ListMap[String, ReferenceOr[Example]] = ListMap.empty,
    encoding: ListMap[String, Encoding] = ListMap.empty,
    extensions: ListMap[String, ExtensionValue] = ListMap.empty
)

case class Encoding(
    contentType: Option[String] = None,
    headers: ListMap[String, ReferenceOr[Header]] = ListMap.empty,
    style: Option[ParameterStyle.ParameterStyle] = None,
    explode: Option[Boolean] = None,
    allowReserved: Option[Boolean] = None,
    extensions: ListMap[String, ExtensionValue] = ListMap.empty
)

sealed trait ResponsesKey
case object ResponsesDefaultKey extends ResponsesKey
case class ResponsesCodeKey(code: Int) extends ResponsesKey

// todo: links
case class Response(
    description: String,
    headers: ListMap[String, ReferenceOr[Header]] = ListMap.empty,
    content: ListMap[String, MediaType] = ListMap.empty,
    extensions: ListMap[String, ExtensionValue] = ListMap.empty
)

case class Responses(
    responses: ListMap[ResponsesKey, ReferenceOr[Response]],
    extensions: ListMap[String, ExtensionValue] = ListMap.empty
)

object Response {
  val Empty: Response = Response("", ListMap.empty, ListMap.empty)
}

case class Example(
    summary: Option[String] = None,
    description: Option[String] = None,
    value: Option[ExampleValue] = None,
    externalValue: Option[String] = None,
    extensions: ListMap[String, ExtensionValue] = ListMap.empty
)

case class Header(
    description: Option[String] = None,
    required: Option[Boolean] = None,
    deprecated: Option[Boolean] = None,
    allowEmptyValue: Option[Boolean] = None,
    style: Option[ParameterStyle.ParameterStyle] = None,
    explode: Option[Boolean] = None,
    allowReserved: Option[Boolean] = None,
    schema: Option[ReferenceOr[Schema]] = None,
    example: Option[ExampleValue] = None,
    examples: ListMap[String, ReferenceOr[Example]] = ListMap.empty,
    content: ListMap[String, MediaType] = ListMap.empty
)
