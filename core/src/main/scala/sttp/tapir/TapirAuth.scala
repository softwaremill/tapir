package sttp.tapir

import java.util.Base64

import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.EndpointInput.Auth
import sttp.tapir.model.UsernamePassword

import scala.collection.immutable.ListMap

object TapirAuth {
  private val BasicAuthType = "Basic"
  private val BearerAuthType = "Bearer"

  def apiKey[T](input: EndpointInput.Single[T]): EndpointInput.Auth.ApiKey[T] = EndpointInput.Auth.ApiKey[T](input)
  val basic: EndpointInput.Auth.Http[UsernamePassword] = httpAuth(BasicAuthType, credentialsCodec(BasicAuthType).map(usernamePasswordCodec))
  val bearer: EndpointInput.Auth.Http[String] = httpAuth(BearerAuthType, credentialsCodec(BearerAuthType))

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
        header[String]("Authorization").map(credentialsCodec(BearerAuthType))
      )
  }

  private def httpAuth[T](authType: String, codec: Codec[String, T, TextPlain]): EndpointInput.Auth.Http[T] =
    EndpointInput.Auth.Http(authType, header[String]("Authorization").map(codec))

  private def usernamePasswordCodec: Codec[String, UsernamePassword, TextPlain] = {
    def decode(s: String): DecodeResult[UsernamePassword] =
      try {
        val s2 = new String(Base64.getDecoder.decode(s))
        val up = s2.split(":", 2) match {
          case Array()      => UsernamePassword("", None)
          case Array(u)     => UsernamePassword(u, None)
          case Array(u, "") => UsernamePassword(u, None)
          case Array(u, p)  => UsernamePassword(u, Some(p))
        }
        DecodeResult.Value(up)
      } catch {
        case e: Exception => DecodeResult.Error(s, e)
      }

    def encode(up: UsernamePassword): String =
      Base64.getEncoder.encodeToString(s"${up.username}:${up.password.getOrElse("")}".getBytes("UTF-8"))

    Codec.fromDecodeNoMeta(decode)(encode)
  }

  private def credentialsCodec(authType: String): Codec[String, String, TextPlain] = {
    val authTypeWithSpace = authType + " "
    val prefixLength = authTypeWithSpace.length
    def removeAuthType(v: String): DecodeResult[String] =
      if (v.startsWith(authType)) DecodeResult.Value(v.substring(prefixLength))
      else DecodeResult.Error(v, new IllegalArgumentException(s"The given value doesn't start with $authType"))
    Codec.string.mapDecode(removeAuthType)(v => s"$authType $v")
  }
}
