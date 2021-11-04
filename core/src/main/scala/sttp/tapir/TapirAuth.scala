package sttp.tapir

import sttp.model.HeaderNames
import sttp.model.headers.WWWAuthenticateChallenge
import sttp.tapir.EndpointInput.Auth

import scala.collection.immutable.ListMap

object TapirAuth {

  /** Reads authorization data from the given `input`. */
  def apiKey[T](
      input: EndpointInput.Single[T],
      challenge: WWWAuthenticateChallenge = WWWAuthenticateChallenge("ApiKey")
  ): EndpointInput.Auth[T, EndpointInput.AuthInfo.ApiKey] = EndpointInput.Auth(input, None, challenge, EndpointInput.AuthInfo.ApiKey())

  /** Reads authorization data from the `Authorization` header, removing the `Basic ` prefix. To parse the data as a base64-encoded
    * username/password combination, use: `basic[UsernamePassword]`
    * @see
    *   UsernamePassword
    */
  def basic[T: Codec[List[String], *, CodecFormat.TextPlain]](
      challenge: WWWAuthenticateChallenge = WWWAuthenticateChallenge.basic
  ): EndpointInput.Auth[T, EndpointInput.AuthInfo.Http] = http(WWWAuthenticateChallenge.BasicScheme, challenge)

  /** Reads authorization data from the `Authorization` header, removing the `Bearer ` prefix. */
  def bearer[T: Codec[List[String], *, CodecFormat.TextPlain]](
      challenge: WWWAuthenticateChallenge = WWWAuthenticateChallenge.bearer
  ): EndpointInput.Auth[T, EndpointInput.AuthInfo.Http] = http(WWWAuthenticateChallenge.BearerScheme, challenge)

  def http[T: Codec[List[String], *, CodecFormat.TextPlain]](
      authScheme: String,
      challenge: WWWAuthenticateChallenge
  ): EndpointInput.Auth[T, EndpointInput.AuthInfo.Http] = {
    val codec = implicitly[Codec[List[String], T, CodecFormat.TextPlain]]
    val authCodec =
      Codec.list(Codec.string.map(stringPrefixWithSpace(authScheme))).mapDecode(codec.decode)(codec.encode).schema(codec.schema)
    EndpointInput.Auth(header[T](HeaderNames.Authorization)(authCodec), None, challenge, EndpointInput.AuthInfo.Http(authScheme))
  }

  object oauth2 {
    def authorizationCode(
        authorizationUrl: Option[String] = None,
        scopes: ListMap[String, String] = ListMap(),
        tokenUrl: Option[String] = None,
        refreshUrl: Option[String] = None,
        challenge: WWWAuthenticateChallenge = WWWAuthenticateChallenge.bearer
    ): Auth[String, EndpointInput.AuthInfo.OAuth2] = {
      EndpointInput.Auth(
        header[String](HeaderNames.Authorization).map(stringPrefixWithSpace(WWWAuthenticateChallenge.BearerScheme)),
        None,
        challenge,
        EndpointInput.AuthInfo.OAuth2(authorizationUrl, tokenUrl, scopes, refreshUrl)
      )
    }
  }

  private def stringPrefixWithSpace(prefix: String) = Mapping.stringPrefixCaseInsensitive(prefix + " ")
}
