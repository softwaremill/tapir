package sttp.tapir.docs.apispec

import sttp.tapir.internal._
import sttp.tapir.apispec.{OAuthFlow, OAuthFlows, SecurityScheme}
import sttp.tapir.docs.apispec.DocsExtensionAttribute.RichEndpointAuth
import sttp.tapir.{AnyEndpoint, EndpointIO, EndpointInput}

import scala.annotation.tailrec

private[docs] object SecuritySchemesForEndpoints {

  /** @param apiKeyAuthTypeName
    *   specifies the type name of api key security schema for header, query or cookie input. For openAPI
    *   https://swagger.io/docs/specification/authentication/api-keys/ valid name is `apiKey`. For asyncAPI
    *   https://www.asyncapi.com/docs/specifications/v2.2.0#securitySchemeObject valid name is `httpApiKey`.
    */
  def apply(es: Iterable[AnyEndpoint], apiKeyAuthTypeName: String): SecuritySchemes = {
    // discarding emptyAuth-s as they are only a marker that authentication is optional
    val auths = es.flatMap(e => e.auths).filterNot(_.isInputEmpty)
    val authSecuritySchemes = auths.map(a => (a, authToSecurityScheme(a, apiKeyAuthTypeName)))
    val securitySchemes = authSecuritySchemes.map { case (auth, scheme) => auth.securitySchemeName -> scheme }.toSet
    val takenNames = authSecuritySchemes.flatMap(_._1.securitySchemeName).toSet
    val namedSecuritySchemes = nameSecuritySchemes(securitySchemes.toVector, takenNames, Map())

    authSecuritySchemes.map { case (a, s) => a -> ((namedSecuritySchemes(s), s)) }.toMap
  }

  @tailrec
  private def nameSecuritySchemes(
      schemes: Vector[(Option[String], SecurityScheme)],
      takenNames: Set[SchemeName],
      acc: Map[SecurityScheme, SchemeName]
  ): Map[SecurityScheme, SchemeName] = {
    schemes.headAndTail match {
      case Some(((Some(name), scheme), tail)) =>
        nameSecuritySchemes(tail, takenNames, acc + (scheme -> name))
      case Some(((None, scheme), tail)) =>
        val baseName = scheme.`type` + "Auth"
        val name = uniqueName(baseName, !takenNames.contains(_))
        nameSecuritySchemes(tail, takenNames + name, acc + (scheme -> name))
      case None => acc
    }
  }

  private def authToSecurityScheme(a: EndpointInput.Auth[_, _ <: EndpointInput.AuthType], apiKeyAuthTypeName: String): SecurityScheme = {
    val extensions = DocsExtensions.fromIterable(a.docsExtensions)
    a.authType match {
      case EndpointInput.AuthType.ApiKey() =>
        val (name, in) = apiKeyInputNameAndIn(a.input.asVectorOfBasicInputs())
        SecurityScheme(apiKeyAuthTypeName, a.info.description, Some(name), Some(in), None, a.info.bearerFormat, None, None, extensions)
      case EndpointInput.AuthType.Http(scheme) =>
        SecurityScheme("http", a.info.description, None, None, Some(scheme.toLowerCase()), a.info.bearerFormat, None, None, extensions)
      case EndpointInput.AuthType.OAuth2(authorizationUrl, tokenUrl, scopes, refreshUrl) =>
        SecurityScheme(
          "oauth2",
          a.info.description,
          None,
          None,
          None,
          a.info.bearerFormat,
          Some(OAuthFlows(authorizationCode = Some(OAuthFlow(authorizationUrl, tokenUrl, refreshUrl, scopes)))),
          None,
          extensions
        )
      case EndpointInput.AuthType.ScopedOAuth2(EndpointInput.AuthType.OAuth2(authorizationUrl, tokenUrl, scopes, refreshUrl), _) =>
        SecurityScheme(
          "oauth2",
          a.info.description,
          None,
          None,
          None,
          a.info.bearerFormat,
          Some(OAuthFlows(authorizationCode = Some(OAuthFlow(authorizationUrl, tokenUrl, refreshUrl, scopes)))),
          None,
          extensions
        )
      case _ => throw new RuntimeException("Impossible, but the compiler complains.")
    }
  }

  private def apiKeyInputNameAndIn(input: Vector[EndpointInput.Basic[_]]) =
    input match {
      case Vector(EndpointIO.Header(name, _, _))    => (name, "header")
      case Vector(EndpointInput.Query(name, _, _))  => (name, "query")
      case Vector(EndpointInput.Cookie(name, _, _)) => (name, "cookie")
      case _ => throw new IllegalArgumentException(s"Api key authentication can only be read from headers, queries or cookies, not: $input")
    }
}
