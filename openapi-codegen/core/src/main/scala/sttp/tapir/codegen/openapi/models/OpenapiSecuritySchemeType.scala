package sttp.tapir.codegen.openapi.models

sealed trait OpenapiSecuritySchemeType

object OpenapiSecuritySchemeType {
  case object OpenapiSecuritySchemeBearerType extends OpenapiSecuritySchemeType
  case object OpenapiSecuritySchemeBasicType extends OpenapiSecuritySchemeType
  case class OpenapiSecuritySchemeApiKeyType(in: String, name: String) extends OpenapiSecuritySchemeType

  import io.circe._
  import cats.implicits._

  private implicit val BearerTypeDecoder: Decoder[OpenapiSecuritySchemeBearerType.type] = { (c: HCursor) =>
    for {
      _ <- c.get[String]("type").ensure(DecodingFailure("Given type is not http!", c.history))(_ == "http")
      _ <- c.get[String]("scheme").ensure(DecodingFailure("Given scheme is not bearer!", c.history))(_ == "bearer")
    } yield {
      OpenapiSecuritySchemeBearerType
    }
  }

  private implicit val BasicTypeDecoder: Decoder[OpenapiSecuritySchemeBasicType.type] = { (c: HCursor) =>
    for {
      _ <- c.get[String]("type").ensure(DecodingFailure("Given type is not http!", c.history))(_ == "http")
      _ <- c.get[String]("scheme").ensure(DecodingFailure("Given scheme is not basic!", c.history))(_ == "basic")
    } yield {
      OpenapiSecuritySchemeBasicType
    }
  }

  private val ApiKeyInOptions = List("header", "query", "cookie")

  private implicit val ApiKeyDecoder: Decoder[OpenapiSecuritySchemeApiKeyType] = { (c: HCursor) =>
    for {
      _ <- c.get[String]("type").ensure(DecodingFailure("Given type is not apiKey!", c.history))(_ == "apiKey")
      in <- c.get[String]("in").ensure(DecodingFailure("Invalid apiKey in value!", c.history))(ApiKeyInOptions.contains)
      name <- c.get[String]("name")
    } yield {
      OpenapiSecuritySchemeApiKeyType(in, name)
    }
  }

  implicit val OpenapiSecuritySchemeTypeDecoder: Decoder[OpenapiSecuritySchemeType] =
    List[Decoder[OpenapiSecuritySchemeType]](
      Decoder[OpenapiSecuritySchemeBearerType.type].widen,
      Decoder[OpenapiSecuritySchemeBasicType.type].widen,
      Decoder[OpenapiSecuritySchemeApiKeyType].widen
    ).reduceLeft(_ or _)
}
