package sttp.tapir.docs.asyncapi

import sttp.model.Method
import sttp.tapir.EndpointOutput.WebSocketBodyWrapper
import sttp.tapir.apispec.{Reference, ReferenceOr, Tag, Schema => ASchema, SchemaType => ASchemaType}
import sttp.tapir.asyncapi._
import sttp.tapir.docs.apispec.namedPathComponents
import sttp.tapir.docs.apispec.schema.Schemas
import sttp.tapir.internal.{IterableToListMap, RichEndpoint}
import sttp.tapir.{AnyEndpoint, Codec, CodecFormat, EndpointIO, EndpointInput}

import scala.collection.immutable.ListMap

private[asyncapi] class EndpointToAsyncAPIWebSocketChannel(
    schemas: Schemas,
    codecToMessageKey: Map[Codec[_, _, _ <: CodecFormat], MessageKey],
    options: AsyncAPIDocsOptions
) {
  def apply(
      e: AnyEndpoint,
      ws: WebSocketBodyWrapper[_, _]
  ): (String, ChannelItem) = {
    val inputs = e.asVectorOfBasicInputs(includeAuth = false)
    val pathComponents = namedPathComponents(inputs)
    val method = e.method.getOrElse(Method.GET)

    val queryInputs = inputs.collect { case EndpointInput.Query(name, codec, info) =>
      addMetaDataFromInfo(name, schemas(codec), info)
    }

    val headerInputs = inputs.collect { case EndpointIO.Header(name, codec, info) =>
      addMetaDataFromInfo(name, schemas(codec), info)
    }

    val channelItem = ChannelItem(
      e.info.summary.orElse(e.info.description).orElse(ws.info.description),
      Some(endpointToOperation(options.subscribeOperationId(pathComponents, e), e, ws.wrapped.responses, ws.wrapped.responsesInfo)),
      Some(endpointToOperation(options.publishOperationId(pathComponents, e), e, ws.wrapped.requests, ws.wrapped.requestsInfo)),
      parameters(inputs),
      List(WebSocketChannelBinding(method.method, objectSchemaFromFields(queryInputs), objectSchemaFromFields(headerInputs), None)),
      DocsExtensions.fromIterable(e.info.docsExtensions)
    )

    (e.renderPathTemplate(renderQueryParam = None, includeAuth = false), channelItem)
  }

  private def addMetaDataFromInfo(name: String, schemaRef: ReferenceOr[ASchema], info: EndpointIO.Info[_]): (String, Either[Reference, ASchema]) = {
    schemaRef match {
      case Right(schema) =>
        val schemaWithDescription = if (schema.description.isEmpty) schemaRef.map(_.copy(description = info.description)) else schemaRef
        val schemaWithDeprecation = if (schema.deprecated.isEmpty && info.deprecated) schemaWithDescription.map(_.copy(deprecated = Some(info.deprecated))) else schemaWithDescription
        name -> schemaWithDeprecation
      case _ => name -> schemaRef
    }
  }

  private def parameters(inputs: Vector[EndpointInput.Basic[_]]): ListMap[String, ReferenceOr[Parameter]] = {
    inputs.collect { case EndpointInput.PathCapture(Some(name), codec, info) =>
      name -> Right(Parameter(info.description, schemas(codec).toOption, None, DocsExtensions.fromIterable(info.docsExtensions)))
    }.toListMap
  }

  private def endpointToOperation(
      id: String,
      e: AnyEndpoint,
      codec: Codec[_, _, _ <: CodecFormat],
      operationInfo: EndpointIO.Info[_]
  ): Operation = {
    Operation(
      Some(id),
      e.info.summary,
      e.info.description,
      e.info.tags.map(Tag(_)).toList,
      None,
      Nil,
      Nil,
      codecToMessageKey.get(codec).map(mk => Left(Reference.to("#/components/messages/", mk))),
      DocsExtensions.fromIterable(operationInfo.docsExtensions)
    )
  }

  private def objectSchemaFromFields(fields: Vector[(String, ReferenceOr[ASchema])]): Option[ASchema] = {
    if (fields.isEmpty) None
    else
      Some {
        ASchema(`type` = Some(ASchemaType.Object), properties = fields.toListMap)
      }
  }
}
