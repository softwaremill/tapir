package sttp.tapir.server

import sttp.model.StatusCode
import sttp.tapir._

class AuthenticationFailureHandler(handle: AuthenticationFailureHandler.Handle, next: DecodeFailureHandler) extends DecodeFailureHandler {
  override def apply(ctx: DecodeFailureContext): DecodeFailureHandling = handle(ctx) match {
    case DecodeFailureHandling.NoMatch => next(ctx)
    case handled                       => handled
  }
}

sealed trait AuthChallenge extends Product with Serializable {
  def output: EndpointOutput[Unit]
}
final case class WWWAuthenticate(authType: String, realm: String, charset: Option[String] = None) extends AuthChallenge {
  override def output: EndpointOutput[Unit] = {
    val cs = charset.fold("")(cs => s""",  charset="$cs"""") // TODO szczygi test it too
    header("WWW-Authenticate", s"""$authType realm="$realm"$cs""") // TODO szczygi escape "?
  }
}
case object NoChallenge extends AuthChallenge {
  override def output: EndpointOutput[Unit] = emptyOutput
}

object AuthChallenge {
  def basic(realm: String, charset: Option[String] = None): AuthChallenge = WWWAuthenticate("Basic", realm, charset)
  def bearer(realm: String, charset: Option[String] = None): AuthChallenge = WWWAuthenticate("Bearer", realm, charset)
  def none: AuthChallenge = NoChallenge
}

object AuthenticationFailureHandler {
  private val headerName = "Authorization"
  type Handle = DecodeFailureContext => DecodeFailureHandling

  def handleMissingAuthorizationHeader(authChallenge: AuthChallenge): Handle = {
    case DecodeFailureContext(h: EndpointIO.Header[_], _) if h.name == headerName =>
      response(authChallenge)
    case _ => DecodeFailureHandling.NoMatch
  }

  private def response(challenge: AuthChallenge) = {
    DecodeFailureHandling.response(statusCode(StatusCode.Unauthorized).and(challenge.output))(())
  }
}
