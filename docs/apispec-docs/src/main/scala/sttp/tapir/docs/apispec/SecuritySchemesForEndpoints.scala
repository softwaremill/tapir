package sttp.tapir.docs.apispec

import sttp.tapir.internal._
import sttp.tapir.apispec.{OAuthFlow, OAuthFlows, SecurityScheme}
import sttp.tapir.{AnyEndpoint, EndpointIO, EndpointInput}

import scala.annotation.tailrec

private[docs] object SecuritySchemesForEndpoints {
  def apply(es: Iterable[AnyEndpoint]): SecuritySchemes = {
    val auths = es.flatMap(e => e.securityInput.auths) ++ es.flatMap(e => e.input.auths)
    val authSecuritySchemes = auths.map(a => (a, authToSecurityScheme(a)))
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

  private def authToSecurityScheme(a: EndpointInput.Auth[_, _ <: EndpointInput.AuthInfo]): SecurityScheme =
    a.authInfo match {
      case EndpointInput.AuthInfo.ApiKey() =>
        val (name, in) = apiKeyInputNameAndIn(a.input.asVectorOfBasicInputs())
        SecurityScheme("apiKey", None, Some(name), Some(in), None, None, None, None)
      case EndpointInput.AuthInfo.Http(scheme) =>
        SecurityScheme("http", None, None, None, Some(scheme.toLowerCase()), None, None, None)
      case EndpointInput.AuthInfo.OAuth2(authorizationUrl, tokenUrl, scopes, refreshUrl) =>
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
      case EndpointInput.AuthInfo.ScopedOAuth2(EndpointInput.AuthInfo.OAuth2(authorizationUrl, tokenUrl, scopes, refreshUrl), _) =>
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
      case _ => throw new RuntimeException("Impossible, but the compiler complains.")
    }

  private def apiKeyInputNameAndIn(input: Vector[EndpointInput.Basic[_]]) =
    input match {
      case Vector(EndpointIO.Header(name, _, _))    => (name, "header")
      case Vector(EndpointInput.Query(name, _, _))  => (name, "query")
      case Vector(EndpointInput.Cookie(name, _, _)) => (name, "cookie")
      case _ => throw new IllegalArgumentException(s"Api key authentication can only be read from headers, queries or cookies, not: $input")
    }
}
