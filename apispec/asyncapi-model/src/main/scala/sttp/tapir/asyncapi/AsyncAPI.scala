package sttp.tapir.asyncapi

import sttp.tapir.apispec.{ExampleValue, ExternalDocumentation, ReferenceOr, Schema, SecurityRequirement, SecurityScheme, Tag}

import scala.collection.immutable.ListMap

case class AsyncAPI(
    asyncapi: String = "2.0.0",
    id: Option[String],
    info: Info,
    servers: ListMap[String, Server],
    channels: ListMap[String, ChannelItem],
    components: Option[Components],
    tags: List[Tag],
    externalDocs: Option[ExternalDocumentation]
) {
  def servers(s: ListMap[String, Server]): AsyncAPI = copy(servers = s)

  def tags(t: List[Tag]): AsyncAPI = copy(tags = t)
}

case class Info(
    title: String,
    version: String,
    description: Option[String],
    termsOfService: Option[String],
    contact: Option[Contact],
    license: Option[License]
)

case class Contact(name: Option[String], url: Option[String], email: Option[String])

case class License(name: String, url: Option[String])

case class Server(
    url: String,
    protocol: String,
    protocolVersion: Option[String],
    description: Option[String],
    variables: ListMap[String, ServerVariable],
    security: List[SecurityRequirement],
    bindings: List[ServerBinding]
)
case class ServerVariable(enum: List[String], default: Option[String], description: Option[String], examples: List[String])

case class ChannelItem(
    $ref: Option[String],
    description: Option[String],
    subscribe: Option[Operation],
    publish: Option[Operation],
    parameters: ListMap[String, ReferenceOr[Parameter]],
    bindings: List[ChannelBinding]
)
case class Operation(
    operationId: Option[String],
    summary: Option[String],
    description: Option[String],
    tags: List[Tag],
    externalDocs: Option[ExternalDocumentation],
    bindings: List[OperationBinding],
    traits: List[OperationTrait],
    message: Option[Message]
)

case class OperationTrait(
    operationId: Option[String],
    summary: Option[String],
    description: Option[String],
    tags: List[Tag],
    externalDocs: Option[ExternalDocumentation],
    bindings: List[OperationBinding]
)

case class Parameter(description: Option[String], schema: Option[Schema], location: Option[String])

sealed trait ServerBinding
case class HttpServerBinding() extends ServerBinding
case class WebSocketServerBinding() extends ServerBinding
case class KafkaServerBinding() extends ServerBinding

sealed trait ChannelBinding
case class HttpChannelBinding() extends ChannelBinding
case class WebSocketChannelBinding(method: String, query: Option[Schema], headers: Option[Schema], bindingVersion: Option[String])
    extends ChannelBinding
case class KafkaChannelBinding() extends ChannelBinding

sealed trait OperationBinding
case class HttpOperationBinding(`type`: String, method: Option[String], query: Option[Schema], bindingVersion: Option[String])
    extends OperationBinding
case class WebSocketOperationBinding() extends OperationBinding
case class KafkaOperationBinding(groupId: Option[Schema], clientId: Option[Schema], bindingVersion: Option[String]) extends OperationBinding

sealed trait MessageBinding
case class HttpMessageBinding(headers: Option[Schema], bindingVersion: Option[String]) extends MessageBinding
case class WebSocketMessageBinding() extends MessageBinding
case class KafkaMessageBinding(key: Option[Schema], bindingVersion: Option[String]) extends MessageBinding

case class Message(
    headers: Option[ReferenceOr[Schema]],
    payload: Option[AnyValue],
    correlationId: Option[ReferenceOr[Schema]],
    schemaFormat: Option[String],
    contentType: Option[String],
    name: Option[String],
    title: Option[String],
    summary: Option[String],
    description: Option[String],
    tags: List[Tag],
    externalDocs: Option[ExternalDocumentation],
    bindings: List[MessageBinding],
    examples: ListMap[String, ExampleValue],
    traits: List[ReferenceOr[MessageTrait]]
)

case class MessageTrait(
    headers: Option[ReferenceOr[Schema]],
    correlationId: Option[ReferenceOr[Schema]],
    schemaFormat: Option[String],
    contentType: Option[String],
    name: Option[String],
    title: Option[String],
    summary: Option[String],
    description: Option[String],
    tags: List[Tag],
    externalDocs: Option[ExternalDocumentation],
    bindings: List[MessageBinding],
    examples: ListMap[String, ExampleValue]
)

// TODO: serverBindings, channelBindings, operationBindings, messageBindings
case class Components(
    schemas: ListMap[String, ReferenceOr[Schema]],
    messages: ListMap[String, ReferenceOr[Message]],
    securitySchemes: ListMap[String, ReferenceOr[SecurityScheme]],
    parameters: ListMap[String, ReferenceOr[Parameter]],
    correlationIds: ListMap[String, ReferenceOr[CorrelationId]],
    operationTraits: ListMap[String, ReferenceOr[OperationTrait]],
    messageTraits: ListMap[String, ReferenceOr[MessageTrait]]
)

case class CorrelationId(description: Option[String], location: String)

case class AnyValue(value: String)
