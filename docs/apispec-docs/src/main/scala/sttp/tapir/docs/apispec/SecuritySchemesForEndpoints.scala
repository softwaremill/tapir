package sttp.tapir.docs.apispec

import sttp.apispec.{OAuthFlow, OAuthFlows, SecurityScheme}
import sttp.tapir.internal._
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
        val name = uniqueString(baseName, !takenNames.contains(_))
        nameSecuritySchemes(tail, takenNames + name, acc + (scheme -> name))
      case None => acc
    }
  }

  // It's not a pretty well way to make guess what kind of OAuth2 flow is supposed to be
  // but we don't have other options instead of combination of optional urls.
  // Fortunately, if we take in account that `password` flow is'n recommended to use,
  // according the openapi specification https://swagger.io/docs/specification/authentication/oauth2/,
  // the combination of optional urls turns to be enough (see table describing flows and requirements of fields)
  // Of cause it makes a bit mess, but in couple with the smart constructors for
  // OAuth2 inputs, it should work.
  private def getOAuth2Flow(a: EndpointInput.AuthType.OAuth2): OAuthFlows = a match {
    case EndpointInput.AuthType.OAuth2(
        Some(authorizationUrl),
        Some(tokenUrl),
        scopes,
        refreshUrl
      ) => OAuthFlows(authorizationCode = Some(OAuthFlow(Some(authorizationUrl), Some(tokenUrl), refreshUrl, scopes)))
    case EndpointInput.AuthType.OAuth2(
        None,
        Some(tokenUrl),
        scopes,
        refreshUrl
      ) => OAuthFlows(clientCredentials = Some(OAuthFlow(None, Some(tokenUrl), refreshUrl, scopes)))
    case EndpointInput.AuthType.OAuth2(
        Some(authorizationUrl),
        None,
        scopes,
        refreshUrl
      ) => OAuthFlows(`implicit` = Some(OAuthFlow(Some(authorizationUrl), None, refreshUrl, scopes)))

    case _ => OAuthFlows()
  }

  private def authToSecurityScheme(a: EndpointInput.Auth[_, _ <: EndpointInput.AuthType], apiKeyAuthTypeName: String): SecurityScheme = {
    val extensions = DocsExtensions.fromIterable(a.docsExtensions)
    a.authType match {
      case EndpointInput.AuthType.ApiKey() =>
        val (name, in) = apiKeyInputNameAndIn(a.input.asVectorOfBasicInputs())
        SecurityScheme(apiKeyAuthTypeName, a.info.description, Some(name), Some(in), None, a.info.bearerFormat, None, None, extensions)
      case EndpointInput.AuthType.Http(scheme) =>
        SecurityScheme("http", a.info.description, None, None, Some(scheme.toLowerCase()), a.info.bearerFormat, None, None, extensions)
      case oauth2: EndpointInput.AuthType.OAuth2 =>
        SecurityScheme(
          "oauth2",
          a.info.description,
          None,
          None,
          None,
          a.info.bearerFormat,
          Some(getOAuth2Flow(oauth2)),
          None,
          extensions
        )
      case EndpointInput.AuthType.ScopedOAuth2(oauth2, _) =>
        SecurityScheme(
          "oauth2",
          a.info.description,
          None,
          None,
          None,
          a.info.bearerFormat,
          Some(getOAuth2Flow(oauth2)),
          None,
          extensions
        )
      case _ => throw new RuntimeException("Impossible, but the compiler complains.")
    }
  }

  private def apiKeyInputNameAndIn(input: Vector[EndpointInput.Basic[_]]) =
    input match {
      case Vector(EndpointIO.Header(name, _, _))      => (name, "header")
      case Vector(EndpointInput.Query(name, _, _, _)) => (name, "query")
      case Vector(EndpointInput.Cookie(name, _, _))   => (name, "cookie")
      case _ => throw new IllegalArgumentException(s"Api key authentication can only be read from headers, queries or cookies, not: $input")
    }
}
