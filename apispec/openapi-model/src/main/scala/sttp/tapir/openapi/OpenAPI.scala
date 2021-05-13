package sttp.tapir.openapi

import sttp.tapir.apispec.{ExampleValue, ExtensionValue, Reference, ReferenceOr, Schema, SecurityRequirement, SecurityScheme, Tag}

import scala.collection.immutable.ListMap

final case class OpenAPI(
    openapi: String = "3.0.3",
    info: Info,
    tags: List[Tag] = Nil,
    servers: List[Server] = Nil,
    paths: Paths = Paths.Empty,
    components: Option[Components] = None,
    security: List[SecurityRequirement] = Nil,
    extensions: ListMap[String, ExtensionValue] = ListMap.empty
) {
  def addPathItem(path: String, pathItem: PathItem): OpenAPI = {
    copy(paths = paths.addPathItem(path, pathItem))
  }

  def servers(s: List[Server]): OpenAPI = copy(servers = s)

  def tags(t: List[Tag]): OpenAPI = copy(tags = t)

  def addExtension(key: String, value: ExtensionValue) = copy(extensions = extensions.updated(key, value))
}

final case class Info(
    title: String,
    version: String,
    description: Option[String] = None,
    termsOfService: Option[String] = None,
    contact: Option[Contact] = None,
    license: Option[License] = None,
    extensions: ListMap[String, ExtensionValue] = ListMap.empty
) {
  def addExtension(key: String, value: ExtensionValue) = copy(extensions = extensions.updated(key, value))
}

final case class Contact(
    name: Option[String] = None,
    email: Option[String] = None,
    url: Option[String] = None,
    extensions: ListMap[String, ExtensionValue] = ListMap.empty
) {
  def addExtension(key: String, value: ExtensionValue) = copy(extensions = extensions.updated(key, value))
}

object Contact {
  val Empty: Contact = Contact()
}

final case class License(
    name: String,
    url: Option[String],
    extensions: ListMap[String, ExtensionValue] = ListMap.empty
) {
  def addExtension(key: String, value: ExtensionValue) = copy(extensions = extensions.updated(key, value))
}

final case class Server(
    url: String,
    description: Option[String] = None,
    variables: Option[ListMap[String, ServerVariable]] = None,
    extensions: ListMap[String, ExtensionValue] = ListMap.empty
) {

  def addExtension(key: String, value: ExtensionValue) = copy(extensions = extensions.updated(key, value))

  def description(d: String): Server = copy(description = Some(d))
  def variables(vars: (String, ServerVariable)*): Server = copy(variables = Some(ListMap(vars: _*)))
}

final case class ServerVariable(
    enum: Option[List[String]],
    default: String,
    description: Option[String],
    extensions: ListMap[String, ExtensionValue] = ListMap.empty
) {
  require(`enum`.fold(true)(_.contains(default)), "ServerVariable#default must be one of the values in enum if enum is defined")

  def description(d: String): ServerVariable = copy(description = Some(d))
  def addExtension(key: String, value: ExtensionValue) = copy(extensions = extensions.updated(key, value))
}

// todo: responses, parameters, examples, requestBodies, headers, links, callbacks
final case class Components(
    schemas: ListMap[String, ReferenceOr[Schema]],
    securitySchemes: ListMap[String, ReferenceOr[SecurityScheme]],
    extensions: ListMap[String, ExtensionValue] = ListMap.empty
) {
  def addSchema(key: String, schema: Schema) = copy(schemas = schemas.updated(key, Right(schema)))
  def getLocalSchema(key: String) = schemas.get(key).flatMap(_.toOption)
  def getReferenceToSchema(key: String): Option[Reference] =
    schemas.get(key).map(refOr => refOr.fold(identity, _ => Reference(s"#/components/schemas/$key")))
  def addSecurityScheme(key: String, scheme: SecurityScheme) =
    copy(securitySchemes = securitySchemes.updated(key, Right(scheme)))
  def getLocalSecurityScheme(key: String) = securitySchemes.get(key).flatMap(_.toOption)
  def getReferenceToSecurityScheme(key: String): Option[Reference] =
    securitySchemes.get(key).map(refOr => refOr.fold(identity, _ => Reference(s"#/components/securitySchemes/$key")))
  def addExtension(key: String, value: ExtensionValue) = copy(extensions = extensions.updated(key, value))
}

object Components {
  val Empty: Components = Components(schemas = ListMap.empty, securitySchemes = ListMap.empty, extensions = ListMap.empty)
}

final case class Paths(
    pathItems: ListMap[String, PathItem],
    extensions: ListMap[String, ExtensionValue] = ListMap.empty
) {

  def addPathItem(path: String, pathItem: PathItem): Paths = {
    val pathItem2 = pathItems.get(path) match {
      case None           => pathItem
      case Some(existing) => existing.mergeWith(pathItem)
    }
    val newPathItems = pathItems + (path -> pathItem2)
    copy(pathItems = newPathItems)
  }
  def addExtension(key: String, value: ExtensionValue) = copy(extensions = extensions.updated(key, value))
}

object Paths {
  val Empty: Paths = Paths(pathItems = ListMap.empty, extensions = ListMap.empty)
}

// todo: $ref
final case class PathItem(
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

  def withSummary(updated: String) = copy(summary = Some(updated))
  def withDescription(updated: String) = copy(description = Some(updated))
  def withGet(updated: Operation) = copy(get = Some(updated))
  def withPut(updated: Operation) = copy(put = Some(updated))
  def withPost(updated: Operation) = copy(post = Some(updated))
  def withDelete(updated: Operation) = copy(delete = Some(updated))
  def withDeleteNoBody = withDelete(Operation.Empty)
  def withOptions(updated: Operation) = copy(options = Some(updated))
  def withHead(updated: Operation) = copy(head = Some(updated))
  def withHeadNoBody = withHead(Operation.Empty)
  def withPatch(updated: Operation) = copy(patch = Some(updated))
  def withTrace(updated: Operation) = copy(trace = Some(updated))
  def addServer(server: Server) = copy(servers = servers ++ List(server))
  def addParameter(param: Parameter) = copy(parameters = parameters ++ List(Right(param)))

  def addExtension(key: String, value: ExtensionValue) = copy(extensions = extensions.updated(key, value))
}

// todo: external docs, callbacks, security
final case class Operation(
    tags: List[String] = List.empty,
    summary: Option[String] = None,
    description: Option[String] = None,
    operationId: Option[String] = None,
    parameters: List[ReferenceOr[Parameter]] = List.empty,
    requestBody: Option[ReferenceOr[RequestBody]] = None,
    responses: Responses = Responses.Empty,
    deprecated: Option[Boolean] = None,
    security: List[SecurityRequirement] = List.empty,
    servers: List[Server] = List.empty,
    extensions: ListMap[String, ExtensionValue] = ListMap.empty
) {

  def addTag(updated: String) = copy(tags = tags ++ List(updated))
  def withSummary(updated: String) = copy(summary = Some(updated))
  def withDescription(updated: String) = copy(description = Some(updated))
  def withOperationId(updated: String) = copy(operationId = Some(updated))
  def withRequestBody(updated: RequestBody) = copy(requestBody = Some(Right(updated)))
  def addParameter(param: Parameter) = copy(parameters = parameters ++ List(Right(param)))
  def addResponse(status: Int, updated: Response) = copy(responses = responses.addResponse(status, updated))
  def addDefaultResponse(updated: Response) = copy(responses = responses.addDefault(updated))
  def withDeprecated = copy(deprecated = Some(true))
  def addServer(server: Server) = copy(servers = servers ++ List(server))

  def addExtension(key: String, value: ExtensionValue) = copy(extensions = extensions.updated(key, value))
}

object Operation {
  val Empty: Operation = Operation()
}

final case class Parameter(
    name: String,
    in: ParameterIn.ParameterIn,
    description: Option[String] = None,
    required: Option[Boolean] = None,
    deprecated: Option[Boolean] = None,
    allowEmptyValue: Option[Boolean] = None,
    style: Option[ParameterStyle.ParameterStyle] = None,
    explode: Option[Boolean] = None,
    allowReserved: Option[Boolean] = None,
    schema: Option[ReferenceOr[Schema]],
    example: Option[ExampleValue] = None,
    examples: ListMap[String, ReferenceOr[Example]] = ListMap.empty,
    content: ListMap[String, MediaType] = ListMap.empty,
    extensions: ListMap[String, ExtensionValue] = ListMap.empty
) {
  def withDescription(updated: String) = copy(description = Some(updated))
  def withRequired = copy(required = Some(true))
  def withDeprecated = copy(deprecated = Some(true))
  def withAllowEmpty = copy(allowEmptyValue = Some(true))
  def withStyle(updated: ParameterStyle.ParameterStyle) = copy(style = Some(updated))
  def withExplode = copy(explode = Some(true))
  def withAllowReserved = copy(allowReserved = Some(true))
  def withSchema(updated: Schema) = copy(schema = Some(Right(updated)))
  def withExample(updated: ExampleValue) = copy(example = Some(updated))
  def addExample(key: String, updated: ExampleValue) = copy(examples = examples.updated(key, updated))
  def withMediaType(contentType: String, mediaType: MediaType) = copy(content = content.updated(contentType, mediaType))
  def addExtension(key: String, value: ExtensionValue) = copy(extensions = extensions.updated(key, value))
}

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

final case class RequestBody(
    description: Option[String] = None,
    content: ListMap[String, MediaType] = ListMap.empty,
    required: Option[Boolean] = None,
    extensions: ListMap[String, ExtensionValue] = ListMap.empty
) {
  def addMediaType(contentType: String, updated: MediaType) = copy(content = content.updated(contentType, updated))
  def addExtension(key: String, value: ExtensionValue) = copy(extensions = extensions.updated(key, value))
}
object RequestBody {
  val Empty: RequestBody = RequestBody()
}

final case class MediaType(
    schema: Option[ReferenceOr[Schema]] = None,
    example: Option[ExampleValue] = None,
    examples: ListMap[String, ReferenceOr[Example]] = ListMap.empty,
    encoding: ListMap[String, Encoding] = ListMap.empty,
    extensions: ListMap[String, ExtensionValue] = ListMap.empty
) {
  def withSchema(updated: Schema) = copy(schema = Some(Right(updated)))
  def withExample(updated: ExampleValue) = copy(example = Some(updated))
  def addExample(key: String, updated: ExampleValue) = copy(examples = examples.updated(key, updated))
  def addExtension(key: String, value: ExtensionValue) = copy(extensions = extensions.updated(key, value))
}

object MediaType {
  val Empty: MediaType = MediaType()
}

final case class Encoding(
    contentType: Option[String] = None,
    headers: ListMap[String, ReferenceOr[Header]] = ListMap.empty,
    style: Option[ParameterStyle.ParameterStyle] = None,
    explode: Option[Boolean] = None,
    allowReserved: Option[Boolean] = None,
    extensions: ListMap[String, ExtensionValue] = ListMap.empty
) {
  def withContentType(updated: String) = copy(contentType = Some(updated))
  def addHeader(key: String, header: Header) = copy(headers = headers.updated(key, Right(header)))
  def withStyle(updated: ParameterStyle.ParameterStyle) = copy(style = Some(updated))
  def withExplode = copy(explode = Some(true))
  def withAllowReserved = copy(allowReserved = Some(true))
  def addExtension(key: String, value: ExtensionValue) = copy(extensions = extensions.updated(key, value))
}

object Encoding {
  val Empty: Encoding = Encoding()
}

sealed trait ResponsesKey
case object ResponsesDefaultKey extends ResponsesKey
final case class ResponsesCodeKey(code: Int) extends ResponsesKey

// todo: links
final case class Response(
    description: Option[String] = None,
    headers: ListMap[String, ReferenceOr[Header]] = ListMap.empty,
    content: ListMap[String, MediaType] = ListMap.empty,
    extensions: ListMap[String, ExtensionValue] = ListMap.empty
) {
  def withDescription(updated: String) = copy(description = Some(updated))
  def addHeader(key: String, header: Header) = copy(headers = headers.updated(key, Right(header)))
  def addMediaType(contentType: String, updated: MediaType) = copy(content = content.updated(contentType, updated))
  def addExtension(key: String, value: ExtensionValue) = copy(extensions = extensions.updated(key, value))
}

object Response {
  val Empty: Response = Response(None, ListMap.empty, ListMap.empty)
}

final case class Responses(
    responses: ListMap[ResponsesKey, ReferenceOr[Response]],
    extensions: ListMap[String, ExtensionValue] = ListMap.empty
) {
  def addResponse(status: Int, response: Response) = copy(responses = responses.updated(ResponsesCodeKey(status), Right(response)))
  def addDefault(response: Response) = copy(responses = responses.updated(ResponsesDefaultKey, Right(response)))
  def addExtension(key: String, value: ExtensionValue) = copy(extensions = extensions.updated(key, value))
}

object Responses {
  val Empty: Responses = Responses(ListMap.empty, ListMap.empty)
}

final case class Example(
    summary: Option[String] = None,
    description: Option[String] = None,
    value: Option[ExampleValue] = None,
    externalValue: Option[String] = None,
    extensions: ListMap[String, ExtensionValue] = ListMap.empty
) {
  def withSummary(updated: String) = copy(summary = Some(updated))
  def withDescription(updated: String) = copy(description = Some(updated))
  def withValue(updated: ExampleValue) = copy(value = Some(updated))

  def addExtension(key: String, value: ExtensionValue) = copy(extensions = extensions.updated(key, value))
}

object Example {
  val Empty: Example = Example()
}

final case class Header(
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
) {
  def withDescription(updated: String) = copy(description = Some(updated))
  def withRequired = copy(required = Some(true))
  def withDeprecated = copy(deprecated = Some(true))
  def withAllowEmpty = copy(allowEmptyValue = Some(true))
  def withStyle(updated: ParameterStyle.ParameterStyle) = copy(style = Some(updated))
  def withExplode = copy(explode = Some(true))
  def withAllowReserved = copy(allowReserved = Some(true))
  def withSchema(updated: Schema) = copy(schema = Some(Right(updated)))
  def withExample(updated: ExampleValue) = copy(example = Some(updated))
  def addExample(key: String, updated: ExampleValue) = copy(examples = examples.updated(key, updated))
  def withMediaType(contentType: String, mediaType: MediaType) = copy(content = content.updated(contentType, mediaType))
}

object Header {
  val Empty: Header = Header()
}
