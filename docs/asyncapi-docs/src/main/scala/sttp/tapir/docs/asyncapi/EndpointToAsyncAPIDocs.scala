package sttp.tapir.docs.asyncapi

import sttp.tapir.apispec.SecurityRequirement
import sttp.tapir.asyncapi.{AsyncAPI, Info, Server}
import sttp.tapir.docs.apispec.schema.{SchemasForEndpoints, ToNamedSchemas}
import sttp.tapir.docs.apispec.{DocsExtension, SecuritySchemes, SecuritySchemesForEndpoints, nameAllPathCapturesInEndpoint}
import sttp.tapir.internal._
import sttp.tapir.{EndpointInput, _}

import scala.collection.immutable.{ListMap, ListSet}

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
    val toObjectSchema = new ToNamedSchemas
    val (keyToSchema, schemas) = new SchemasForEndpoints(wsEndpoints, options.schemaName, toObjectSchema).apply()
    val (codecToMessageKey, keyToMessage) = new MessagesForEndpoints(schemas, options.schemaName, toObjectSchema)(
      wsEndpointsWithWrapper.map(_._2)
    )
    val securitySchemes = SecuritySchemesForEndpoints(wsEndpoints)
    val channelCreator = new EndpointToAsyncAPIWebSocketChannel(schemas, codecToMessageKey, options)
    val componentsCreator = new EndpointToAsyncAPIComponents(keyToSchema, keyToMessage, securitySchemes)
    val allSecurityRequirements = securityRequirements(securitySchemes, es)

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

  private def securityRequirements(securitySchemes: SecuritySchemes, es: Iterable[AnyEndpoint]): List[SecurityRequirement] = {
    ListSet(es.toList.flatMap(securityRequirements(securitySchemes, _)): _*).toList
  }

  private def securityRequirements(securitySchemes: SecuritySchemes, e: AnyEndpoint): List[SecurityRequirement] = {
    val auths = e.auths
    val securityRequirement: SecurityRequirement = auths.flatMap {
      case auth @ EndpointInput.Auth(_, _, _, info: EndpointInput.AuthInfo.ScopedOAuth2) =>
        securitySchemes.get(auth).map(_._1).map((_, info.requiredScopes.toVector))
      case auth => securitySchemes.get(auth).map(_._1).map((_, Vector.empty))
    }.toListMap

    val securityOptional = auths.flatMap(_.asVectorOfBasicInputs()).forall(_.codec.schema.isOptional)

    if (securityRequirement.isEmpty) List.empty
    else {
      if (securityOptional) {
        List(ListMap.empty: SecurityRequirement, securityRequirement)
      } else {
        List(securityRequirement)
      }
    }
  }
}
