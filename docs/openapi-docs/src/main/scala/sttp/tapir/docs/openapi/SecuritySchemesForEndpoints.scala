package sttp.tapir.docs.openapi

import sttp.tapir.internal._
import sttp.tapir.openapi.{OAuthFlow, OAuthFlows, SecurityScheme}
import sttp.tapir.{Endpoint, EndpointIO, EndpointInput}

import scala.annotation.tailrec

private[openapi] object SecuritySchemesForEndpoints {
  def apply(es: Iterable[Endpoint[_, _, _, _]]): SecuritySchemes = {
    val auths = es.flatMap(e => e.input.auths)
    val authSecuritySchemes = auths.map(a => (a, authToSecurityScheme(a)))
    val securitySchemes = authSecuritySchemes.map(_._2).toSet
    val namedSecuritySchemes = nameSecuritySchemes(securitySchemes.toVector, Set(), Map())

    authSecuritySchemes.map { case (a, s) => a -> ((namedSecuritySchemes(s), s)) }.toMap
  }

  @tailrec
  private def nameSecuritySchemes(
      schemes: Vector[SecurityScheme],
      takenNames: Set[SchemeName],
      acc: Map[SecurityScheme, SchemeName]
  ): Map[SecurityScheme, SchemeName] = {
    schemes match {
      case Vector() => acc
      case scheme +: tail =>
        val baseName = scheme.`type` + "Auth"
        val name = uniqueName(baseName, !takenNames.contains(_))
        nameSecuritySchemes(tail, takenNames + name, acc + (scheme -> name))
    }
  }

  private def authToSecurityScheme(a: EndpointInput.Auth[_]): SecurityScheme = a match {
    case EndpointInput.Auth.ApiKey(input) =>
      val (name, in) = apiKeyInputNameAndIn(input.asVectorOfBasicInputs())
      SecurityScheme("apiKey", None, Some(name), Some(in), None, None, None, None)
    case EndpointInput.Auth.Http(scheme, _) =>
      SecurityScheme("http", None, None, None, Some(scheme.toLowerCase()), None, None, None)
    case EndpointInput.Auth.Oauth2(authorizationUrl, tokenUrl, scopes, refreshUrl, _) =>
      SecurityScheme(
        "oauth2",
        None,
        None,
        None,
        None,
        None,
        Some(OAuthFlows(authorizationCode = Some(OAuthFlow(authorizationUrl, tokenUrl, refreshUrl, scopes)))),
        None
      )
    case EndpointInput.Auth.ScopedOauth2(EndpointInput.Auth.Oauth2(authorizationUrl, tokenUrl, scopes, refreshUrl, _), _) =>
      SecurityScheme(
        "oauth2",
        None,
        None,
        None,
        None,
        None,
        Some(OAuthFlows(authorizationCode = Some(OAuthFlow(authorizationUrl, tokenUrl, refreshUrl, scopes)))),
        None
      )
  }

  private def apiKeyInputNameAndIn(input: Vector[EndpointInput.Basic[_]]) = input match {
    case Vector(EndpointIO.Header(name, _, _))    => (name, "header")
    case Vector(EndpointInput.Query(name, _, _))  => (name, "query")
    case Vector(EndpointInput.Cookie(name, _, _)) => (name, "cookie")
    case _                                        => throw new IllegalArgumentException(s"Api key authentication can only be read from headers, queries or cookies, not: $input")
  }
}
