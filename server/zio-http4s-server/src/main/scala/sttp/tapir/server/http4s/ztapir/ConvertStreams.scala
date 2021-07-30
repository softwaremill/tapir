package sttp.tapir.server.http4s.ztapir

import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.{EndpointIO, EndpointInput}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.ztapir.ZServerEndpoint
import zio.RIO

object ConvertStreams {

  def apply[R, I, E, O](se: ZServerEndpoint[R, I, E, O]): ServerEndpoint[I, E, O, Fs2Streams[RIO[R, *]] with WebSockets, RIO[R, *]] = {}

  private def apply(input: EndpointInput[_]): EndpointInput[_] = {
    input match {
      // streaming inputs
      case EndpointIO.StreamBodyWrapper(wrapped)
      // traversing wrapped inputs
      case EndpointInput.Pair(left, right, combine, split) => EndpointInput.Pair(apply(left), apply(right), combine, split)
      case EndpointIO.Pair(left, right, combine, split) =>
        EndpointIO.Pair(apply(left).asInstanceOf[EndpointIO[_]], apply(right).asInstanceOf[EndpointIO[_]], combine, split)
      case EndpointInput.MappedPair(wrapped, mapping) =>
        EndpointInput.MappedPair(apply(wrapped).asInstanceOf[EndpointInput.Pair[_, _, Any]], mapping)
      case EndpointIO.MappedPair(wrapped, mapping) =>
        EndpointIO.MappedPair(apply(wrapped).asInstanceOf[EndpointIO.Pair[_, _, Any]], mapping)
      case EndpointInput.Auth.ApiKey(wrapped, challenge, securitySchemeName) =>
        EndpointInput.Auth.ApiKey(apply(wrapped).asInstanceOf[EndpointInput.Single[_]], challenge, securitySchemeName)
      case EndpointInput.Auth.Http(scheme, wrapped, challenge, securitySchemeName) =>
        EndpointInput.Auth.Http(scheme, apply(wrapped).asInstanceOf[EndpointInput.Single[_]], challenge, securitySchemeName)
      case EndpointInput.Auth.Oauth2(authorizationUrl, tokenUrl, scopes, refreshUrl, wrapped, challenge, securitySchemeName) =>
        EndpointInput.Auth.Oauth2(
          authorizationUrl,
          tokenUrl,
          scopes,
          refreshUrl,
          apply(wrapped).asInstanceOf[EndpointInput.Single[_]],
          challenge,
          securitySchemeName
        )
      case EndpointInput.Auth.ScopedOauth2(oauth2, requiredScopes) =>
        EndpointInput.Auth.ScopedOauth2(apply(oauth2).asInstanceOf[EndpointInput.Auth.Oauth2[_]], requiredScopes)
      // all other cases - unchanged
      case _ => input
    }
  }

  private def apply(streamBodyWrapper:)
}
