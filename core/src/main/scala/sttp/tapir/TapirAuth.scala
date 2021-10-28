package sttp.tapir

import sttp.tapir.EndpointInput.{Auth, WWWAuthenticate}

import scala.collection.immutable.ListMap

object TapirAuth {
  private val BasicAuthType = "Basic"
  private val BearerAuthType = "Bearer"

  /** Reads authorization data from the given `input`.
    */
  def apiKey[T](
      input: EndpointInput.Single[T],
      challenge: WWWAuthenticate = WWWAuthenticate.apiKey()
  ): EndpointInput.Auth.ApiKey[T] = EndpointInput.Auth.ApiKey[T](input, challenge, None)

  /** Reads authorization data from the `Authorization` header, removing the `Basic ` prefix. To parse the data as a base64-encoded
    * username/password combination, use: `basic[UsernamePassword]`
    * @see
    *   UsernamePassword
    */
  def basic[T: Codec[List[String], *, CodecFormat.TextPlain]](
      challenge: WWWAuthenticate = WWWAuthenticate.basic()
  ): EndpointInput.Auth.Http[T] = httpAuth(BasicAuthType, challenge)

  /** Reads authorization data from the `Authorization` header, removing the `Bearer ` prefix.
    */
  def bearer[T: Codec[List[String], *, CodecFormat.TextPlain]](
      challenge: WWWAuthenticate = WWWAuthenticate.bearer()
  ): EndpointInput.Auth.Http[T] = httpAuth(BearerAuthType, challenge)

  object oauth2 {
    def authorizationCode(
        authorizationUrl: Option[String] = None,
        scopes: ListMap[String, String] = ListMap(),
        tokenUrl: Option[String] = None,
        refreshUrl: Option[String] = None,
        challenge: WWWAuthenticate = WWWAuthenticate.bearer()
    ): Auth.Oauth2[String] =
      EndpointInput.Auth.Oauth2(
        authorizationUrl,
        tokenUrl,
        scopes,
        refreshUrl,
        header[String]("Authorization").map(stringPrefixWithSpace(BearerAuthType)),
        challenge,
        None
      )
  }

  private def httpAuth[T: Codec[List[String], *, CodecFormat.TextPlain]](
      authType: String,
      challenge: WWWAuthenticate
  ): EndpointInput.Auth.Http[T] = {
    val codec = implicitly[Codec[List[String], T, CodecFormat.TextPlain]]
    val authCodec = Codec.list(Codec.string.map(stringPrefixWithSpace(authType))).mapDecode(codec.decode)(codec.encode).schema(codec.schema)
    EndpointInput.Auth.Http(authType, header[T]("Authorization")(authCodec), challenge, None)
  }

  private def stringPrefixWithSpace(prefix: String) = Mapping.stringPrefixCaseInsensitive(prefix + " ")
}
