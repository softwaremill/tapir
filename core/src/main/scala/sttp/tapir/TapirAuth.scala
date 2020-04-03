package sttp.tapir

import java.util.Base64

import sttp.tapir.EndpointInput.Auth
import sttp.tapir.model.UsernamePassword

import scala.collection.immutable.ListMap

object TapirAuth {
  private val BasicAuthType = "Basic"
  private val BearerAuthType = "Bearer"

  def apiKey[T](input: EndpointInput.Single[T]): EndpointInput.Auth.ApiKey[T] = EndpointInput.Auth.ApiKey[T](input)
  val basic: EndpointInput.Auth.Http[UsernamePassword] =
    httpAuth(BasicAuthType, stringPrefixWithSpace(BasicAuthType).map(usernamePasswordMapping))
  val bearer: EndpointInput.Auth.Http[String] = httpAuth(BearerAuthType, stringPrefixWithSpace(BearerAuthType))

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

  private def httpAuth[T](authType: String, mapping: Mapping[String, T]): EndpointInput.Auth.Http[T] =
    EndpointInput.Auth.Http(authType, header[String]("Authorization").map(mapping))

  private def stringPrefixWithSpace(prefix: String) = Mapping.stringPrefix(prefix + " ")

  def usernamePasswordMapping: Mapping[String, UsernamePassword] = {
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

    Mapping.fromDecode(decode)(encode)
  }
}
