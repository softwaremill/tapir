package sttp.tapir.server.interceptor.cors

import sttp.model.headers.Origin
import sttp.model.{Method, StatusCode}
import sttp.tapir.server.interceptor.cors.CORSConfig._

import scala.concurrent.duration.Duration

case class CORSConfig(
    allowedOrigin: AllowedOrigin,
    allowedCredentials: AllowedCredentials,
    allowedMethods: AllowedMethods,
    allowedHeaders: AllowedHeaders,
    exposedHeaders: ExposedHeaders,
    maxAge: MaxAge,
    preflightResponseStatusCode: StatusCode
) {

  /** Allows CORS requests from any origin.
    *
    * Sets the `Access-Control-Allow-Origin` response header to `*`.
    */
  def allowAllOrigins: CORSConfig = copy(allowedOrigin = AllowedOrigin.All)

  /** Allows CORS requests only from a specific origin.
    *
    * If the request `Origin` matches the given `origin`, sets the `Access-Control-Allow-Origin` response header to the given `origin`.
    * Otherwise the `Access-Control-Allow-Origin` response header is suppressed.
    */
  def allowOrigin(origin: Origin): CORSConfig = copy(allowedOrigin = AllowedOrigin.Single(origin))

  /** Allows CORS requests from origins matching predicate
    *
    * If the request `Origin` header matches the given `predicate`, sets the `Access-Control-Allow-Origin` response header to the given
    * `origin`. Otherwise the `Access-Control-Allow-Origin` response header is suppressed.
    */
  def allowMatchingOrigins(predicate: String => Boolean): CORSConfig = copy(allowedOrigin = AllowedOrigin.Matching(predicate))

  /** Allows credentialed requests by setting the `Access-Control-Allow-Credentials` response header to `true`
    */
  def allowCredentials: CORSConfig = copy(allowedCredentials = AllowedCredentials.Allow)

  /** Blocks credentialed requests by suppressing the `Access-Control-Allow-Credentials` response header
    */
  def denyCredentials: CORSConfig = copy(allowedCredentials = AllowedCredentials.Deny)

  /** Allows CORS requests using any method.
    *
    * Sets the `Access-Control-Allow-Methods` response header to `*`
    */
  def allowAllMethods: CORSConfig = copy(allowedMethods = AllowedMethods.All)

  /** Allows CORS requests using specific methods.
    *
    * If the preflight request's `Access-Control-Request-Method` header requests one of the specified `methods`, the
    * `Access-Control-Allow-Methods` response header is set to a comma-separated list of the given `methods`. Otherwise the
    * `Access-Control-Allow-Methods` response header is suppressed.
    */
  def allowMethods(methods: Method*): CORSConfig = copy(allowedMethods = AllowedMethods.Some(methods.toSet))

  /** Allows CORS requests with any headers.
    *
    * Sets the `Access-Control-Allow-Headers` response header to `*`
    */
  def allowAllHeaders: CORSConfig = copy(allowedHeaders = AllowedHeaders.All)

  /** Allows CORS requests using specific headers.
    *
    * If the preflight request's `Access-Control-Request-Headers` header requests one of the specified `headers`, the
    * `Access-Control-Allow-Headers` response header is set to a comma-separated list of the given `headers`. Otherwise the
    * `Access-Control-Allow-Headers` response header is suppressed.
    */
  def allowHeaders(headerNames: String*): CORSConfig = copy(allowedHeaders = AllowedHeaders.Some(headerNames.toSet))

  /** Allows CORS requests using any headers requested in preflight request's `Access-Control-Request-Headers` header.
    *
    * Use [[reflectHeaders]] instead of [[allowAllHeaders]] when credentialed requests are enabled with [[allowCredentials]], since
    * wildcards are illegal when credentials are enabled
    */
  def reflectHeaders: CORSConfig = copy(allowedHeaders = AllowedHeaders.Reflect)

  /** Exposes all response headers to JavaScript in browsers
    *
    * Sets the `Access-Control-Expose-Headers` response header to `*`
    */
  def exposeAllHeaders: CORSConfig = copy(exposedHeaders = ExposedHeaders.All)

  /** Exposes no response headers to JavaScript in browsers
    *
    * Suppresses the `Access-Control-Expose-Headers` response header
    */
  def exposeNoHeaders: CORSConfig = copy(exposedHeaders = ExposedHeaders.None)

  /** Exposes specific response headers to JavaScript in browsers
    *
    * Sets the `Access-Control-Expose-Headers` response header to a comma-separated list of the given `headerNames`
    */
  def exposeHeaders(headerNames: String*): CORSConfig = copy(exposedHeaders = ExposedHeaders.Some(headerNames.toSet))

  /** Determines how long the response to a preflight request can be cached by the client.
    *
    * Suppresses the `Access-Control-Max-Age` response header, which makes the client use its default value.
    */
  def defaultMaxAge: CORSConfig = copy(maxAge = MaxAge.Default)

  /** Determines how long the response to a preflight request can be cached by the client.
    *
    * Sets the `Access-Control-Max-Age` response header to the given `duration` in seconds.
    */
  def maxAge(duration: Duration): CORSConfig = copy(maxAge = MaxAge.Some(duration))

  /** Sets the response status code of successful preflight requests to "204 No Content"
    */
  def defaultPreflightResponseStatusCode: CORSConfig = copy(preflightResponseStatusCode = StatusCode.NoContent)

  /** Sets the response status code of successful preflight requests to the given `statusCode`
    */
  def preflightResponseStatusCode(statusCode: StatusCode): CORSConfig = copy(preflightResponseStatusCode = statusCode)

  /** When credentialed requests are enabled, any wildcard in allowed origin/headers/methods is illegal */
  private[cors] def isValid: Boolean =
    allowedCredentials == AllowedCredentials.Deny || (allowedOrigin != AllowedOrigin.All && allowedHeaders != AllowedHeaders.All && allowedMethods != AllowedMethods.All)
}

object CORSConfig {
  val default: CORSConfig = CORSConfig(
    allowedOrigin = AllowedOrigin.All,
    allowedCredentials = AllowedCredentials.Deny,
    allowedMethods = AllowedMethods.Some(Set(Method.GET, Method.HEAD, Method.POST, Method.PUT, Method.DELETE)),
    allowedHeaders = AllowedHeaders.Reflect,
    exposedHeaders = ExposedHeaders.None,
    maxAge = MaxAge.Default,
    preflightResponseStatusCode = StatusCode.NoContent
  )

  sealed trait AllowedOrigin
  object AllowedOrigin {
    case object All extends AllowedOrigin
    case class Single(origin: Origin) extends AllowedOrigin
    case class Matching(predicate: String => Boolean) extends AllowedOrigin
  }

  sealed trait AllowedCredentials
  object AllowedCredentials {
    case object Allow extends AllowedCredentials
    case object Deny extends AllowedCredentials
  }

  sealed trait AllowedMethods
  object AllowedMethods {
    case object All extends AllowedMethods
    case class Some(methods: Set[Method]) extends AllowedMethods
  }

  sealed trait AllowedHeaders
  object AllowedHeaders {
    case object All extends AllowedHeaders
    case class Some(headersNames: Set[String]) extends AllowedHeaders
    case object Reflect extends AllowedHeaders
  }

  sealed trait ExposedHeaders
  object ExposedHeaders {
    case object All extends ExposedHeaders
    case class Some(headerNames: Set[String]) extends ExposedHeaders
    case object None extends ExposedHeaders
  }

  sealed trait MaxAge
  object MaxAge {
    case class Some(duration: Duration) extends MaxAge
    case object Default extends MaxAge
  }
}
