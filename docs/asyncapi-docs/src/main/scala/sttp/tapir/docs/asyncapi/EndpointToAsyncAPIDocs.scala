package sttp.tapir.docs.asyncapi

import sttp.apispec.asyncapi.{AsyncAPI, Info, Server}
import sttp.tapir._
import sttp.tapir.docs.apispec.schema.SchemasForEndpoints
import sttp.tapir.docs.apispec._
import sttp.tapir.internal._

private[asyncapi] object EndpointToAsyncAPIDocs {
  def toAsyncAPI(
      info: Info,
      servers: Iterable[(String, Server)],
      es: Iterable[AnyEndpoint],
      options: AsyncAPIDocsOptions,
      docsExtensions: List[DocsExtension[_]]
  ): AsyncAPI = {
    val wsEndpointsWithWrapper = es.map(e => (e, findWebSocket(e))).collect { case (e, Some(ws)) => (e, ws) }
    val wsEndpoints = wsEndpointsWithWrapper.map(_._1).map(nameAllPathCapturesInEndpoint)
    val (keyToSchema, schemas) =
      new SchemasForEndpoints(
        wsEndpoints,
        options.schemaName,
        markOptionsAsNullable = false,
        failOnDuplicateSchemaName = false,
        additionalOutputs = Nil
      )
        .apply()
    val (codecToMessageKey, keyToMessage) = new MessagesForEndpoints(schemas, options.schemaName)(
      wsEndpointsWithWrapper.map(_._2)
    )
    val securitySchemes = SecuritySchemesForEndpoints(wsEndpoints, apiKeyAuthTypeName = "httpApiKey")
    val channelCreator = new EndpointToAsyncAPIWebSocketChannel(schemas, codecToMessageKey, options)
    val componentsCreator = new EndpointToAsyncAPIComponents(keyToSchema, keyToMessage, securitySchemes)
    val securityRequirementsForEndpoints = new SecurityRequirementsForEndpoints(securitySchemes)
    val allSecurityRequirements = securityRequirementsForEndpoints(es)

    val channels = wsEndpointsWithWrapper.map { case (e, ws) => channelCreator(e, ws) }

    AsyncAPI(
      id = None,
      info = info,
      servers = servers.map { case (n, s) => (n, s.copy(security = allSecurityRequirements)) }.toListMap,
      channels = channels.map { case (p, c) => (p, Right(c)) }.toListMap,
      components = componentsCreator.components,
      tags = List.empty,
      externalDocs = None,
      extensions = DocsExtensions.fromIterable(docsExtensions)
    )
  }
}
