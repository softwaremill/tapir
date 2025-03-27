package sttp.tapir.codegen.openapi.models

sealed trait OpenapiSecuritySchemeType

object OpenapiSecuritySchemeType {
  case object OpenapiSecuritySchemeBearerType extends OpenapiSecuritySchemeType
  case object OpenapiSecuritySchemeBasicType extends OpenapiSecuritySchemeType
  case class OpenapiSecuritySchemeApiKeyType(in: String, name: String) extends OpenapiSecuritySchemeType
  case class OpenapiSecuritySchemeOAuth2Type(flows: Map[OAuth2FlowType.OAuth2FlowType, OAuth2Flow]) extends OpenapiSecuritySchemeType

  object OAuth2FlowType extends Enumeration {
    val authorizationCode, `implicit`, password, clientCredentials = Value
    type OAuth2FlowType = Value
  }
  case class OAuth2Flow(authorizationUrl: Option[String], tokenUrl: Option[String], refreshUrl: Option[String], scopes: Map[String, String])

  import io.circe._
  import cats.implicits._

  private implicit lazy val BearerTypeDecoder: Decoder[OpenapiSecuritySchemeBearerType.type] = { (c: HCursor) =>
    for {
      _ <- c.get[String]("type").ensure(DecodingFailure("Given type is not http!", c.history))(_ == "http")
      _ <- c.get[String]("scheme").ensure(DecodingFailure("Given scheme is not bearer!", c.history))(_ == "bearer")
    } yield {
      OpenapiSecuritySchemeBearerType
    }
  }

  private implicit lazy val BasicTypeDecoder: Decoder[OpenapiSecuritySchemeBasicType.type] = { (c: HCursor) =>
    for {
      _ <- c.get[String]("type").ensure(DecodingFailure("Given type is not http!", c.history))(_ == "http")
      _ <- c.get[String]("scheme").ensure(DecodingFailure("Given scheme is not basic!", c.history))(_ == "basic")
    } yield {
      OpenapiSecuritySchemeBasicType
    }
  }

  private val ApiKeyInOptions = List("header", "query", "cookie")

  private implicit lazy val ApiKeyDecoder: Decoder[OpenapiSecuritySchemeApiKeyType] = { (c: HCursor) =>
    for {
      _ <- c.get[String]("type").ensure(DecodingFailure("Given type is not apiKey!", c.history))(_ == "apiKey")
      in <- c.get[String]("in").ensure(DecodingFailure("Invalid apiKey in value!", c.history))(ApiKeyInOptions.contains)
      name <- c.get[String]("name")
    } yield {
      OpenapiSecuritySchemeApiKeyType(in, name)
    }
  }

  private implicit lazy val OAuth2FlowDecoder: Decoder[OAuth2Flow] = { (c: HCursor) =>
    for {
      a <- c.get[Option[String]]("authorizationUrl")
      t <- c.get[Option[String]]("tokenUrl")
      r <- c.get[Option[String]]("refreshUrl")
      s <- c.get[Map[String, String]]("scopes")
    } yield {
      OAuth2Flow(a, t, r, s)
    }
  }

  private implicit lazy val OpenapiSecuritySchemeOAuth2TypeDecoder: Decoder[OpenapiSecuritySchemeOAuth2Type] = { (c: HCursor) =>
    for {
      fs <- c
        .get[JsonObject]("flows")
        .ensure(DecodingFailure("Only authorizationCode, implicit, password and clientCredentials flows are supported", c.history))(
          _.keys.toSet.--(Set("authorizationCode", "implicit", "password", "clientCredentials")).isEmpty
        )
      parsedFs <- fs.toMap.toList
        .traverse {
          case ("authorizationCode", v) => v.as[OAuth2Flow].map(OAuth2FlowType.authorizationCode -> _)
          case ("implicit", v)          => v.as[OAuth2Flow].map(OAuth2FlowType.`implicit` -> _)
          case ("password", v)          => v.as[OAuth2Flow].map(OAuth2FlowType.password -> _)
          case ("clientCredentials", v) => v.as[OAuth2Flow].map(OAuth2FlowType.clientCredentials -> _)
        }
        .map(_.toMap)
    } yield {
      OpenapiSecuritySchemeOAuth2Type(parsedFs)
    }
  }

  implicit lazy val OpenapiSecuritySchemeTypeDecoder: Decoder[OpenapiSecuritySchemeType] =
    List[Decoder[OpenapiSecuritySchemeType]](
      Decoder[OpenapiSecuritySchemeBearerType.type].widen,
      Decoder[OpenapiSecuritySchemeBasicType.type].widen,
      Decoder[OpenapiSecuritySchemeApiKeyType].widen,
      Decoder[OpenapiSecuritySchemeOAuth2Type].widen
    ).reduceLeft(_ or _)
}
