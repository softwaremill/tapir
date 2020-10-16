package sttp.tapir.docs.asyncapi

import sttp.tapir.apispec.{IterableToListMap, SecurityRequirement}
import sttp.tapir.asyncapi.{AsyncAPI, Info, Server}
import sttp.tapir.docs.apispec.schema.SchemasForEndpoints
import sttp.tapir.docs.apispec.{SecuritySchemes, SecuritySchemesForEndpoints, nameAllPathCapturesInEndpoint}
import sttp.tapir.internal._
import sttp.tapir.{EndpointInput, _}

import scala.collection.immutable.{ListMap, ListSet}

object EndpointToAsyncAPIDocs {
  def toAsyncAPI(
      info: Info,
      servers: Iterable[(String, Server)],
      es: Iterable[Endpoint[_, _, _, _]],
      options: AsyncAPIDocsOptions
  ): AsyncAPI = {
    type P[A, B] = Nothing // we need to pass the same P to isWebSocket and channelCreator to make the compiler happy
    val wsEndpointsWithWrapper = es.map(e => (e, isWebSocket[P](e))).collect { case (e, Some(ws)) => (e, ws) }
    val wsEndpoints = wsEndpointsWithWrapper.map(_._1).map(nameAllPathCapturesInEndpoint)
    val (keyToSchema, objectSchemas) = SchemasForEndpoints(wsEndpoints)
    val securitySchemes = SecuritySchemesForEndpoints(wsEndpoints)
    val channelCreator = new EndpointToAsyncAPIWebSocketChannel(objectSchemas, options)
    val componentsCreator = new EndpointToAsyncAPIComponents(keyToSchema, securitySchemes)
    val allSecurityRequirements = securityRequirements(securitySchemes, es)

    val channels = wsEndpointsWithWrapper.map { case (e, ws) => channelCreator[P](e, ws) }

    AsyncAPI(
      id = None,
      info = info,
      servers = servers.map { case (n, s) => (n, s.copy(security = allSecurityRequirements)) }.toListMap,
      channels = channels.map { case (p, c) => (p, Right(c)) }.toListMap,
      components = componentsCreator.components,
      tags = List.empty,
      externalDocs = None
    )
  }

  private def securityRequirements(securitySchemes: SecuritySchemes, es: Iterable[Endpoint[_, _, _, _]]): List[SecurityRequirement] = {
    ListSet(es.toList.flatMap(securityRequirements(securitySchemes, _)): _*).toList
  }

  private def securityRequirements(securitySchemes: SecuritySchemes, e: Endpoint[_, _, _, _]): List[SecurityRequirement] = {
    val securityRequirement: SecurityRequirement = e.input.auths.flatMap {
      case auth: EndpointInput.Auth.ScopedOauth2[_] => securitySchemes.get(auth).map(_._1).map((_, auth.requiredScopes.toVector))
      case auth                                     => securitySchemes.get(auth).map(_._1).map((_, Vector.empty))
    }.toListMap

    val securityOptional = e.input.auths.flatMap(_.asVectorOfBasicInputs()).forall(_.codec.schema.exists(_.isOptional))

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
