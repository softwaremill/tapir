package sttp.tapir

import sttp.tapir.EndpointInput.Auth
import sttp.tapir.model.UsernamePassword

import scala.collection.immutable.ListMap

object TapirAuth {
  private val BasicAuthType = "Basic"
  private val BearerAuthType = "Bearer"

  /** Reads authorization data from the given `input`.
    */
  def apiKey[T](input: EndpointInput.Single[T]): EndpointInput.Auth.ApiKey[T] = EndpointInput.Auth.ApiKey[T](input)

  /** Reads authorization data from the `Authorization` header, removing the `Basic ` prefix.
    * To parse the data as a base64-encoded username/password combination, use: `basic[UsernamePassword]`
    * @see UsernamePassword
    */
  def basic[T: Codec[List[String], *, CodecFormat.TextPlain]]: EndpointInput.Auth.Http[UsernamePassword] = httpAuth(BasicAuthType)

  /** Reads authorization data from the `Authorization` header, removing the `Bearer ` prefix.
    */
  def bearer[T: Codec[List[String], *, CodecFormat.TextPlain]]: EndpointInput.Auth.Http[T] = httpAuth(BearerAuthType)

  object oauth2 {
    def authorizationCode(
        authorizationUrl: String,
        tokenUrl: String,
        scopes: ListMap[String, String],
        refreshUrl: Option[String] = None
    ): Auth.Oauth2[String] =
      EndpointInput.Auth.Oauth2(
        authorizationUrl,
        tokenUrl,
        scopes,
        refreshUrl,
        header[String]("Authorization").map(stringPrefixWithSpace(BearerAuthType))
      )
  }

  private def httpAuth[T: Codec[List[String], *, CodecFormat.TextPlain]](authType: String): EndpointInput.Auth.Http[T] = {
    val codec = implicitly[Codec[List[String], T, CodecFormat.TextPlain]]
    val authCodec = Codec.list(Codec.string.map(stringPrefixWithSpace(authType))).map(codec).schema(codec.schema)
    EndpointInput.Auth.Http(authType, header[T]("Authorization")(authCodec))
  }

  private def stringPrefixWithSpace(prefix: String) = Mapping.stringPrefix(prefix + " ")
}
