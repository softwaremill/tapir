package sttp.tapir.codegen.openapi.models

import io.circe.generic.semiauto.deriveDecoder
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.OpenapiSchemaEnum

case class OpenapiServerEnum(enum: Seq[String] = Nil, default: Option[String] = None)
object OpenapiServerEnum {
  import io.circe._
  implicit val serverEnumDecoder: Decoder[OpenapiServerEnum] = { (c: HCursor) =>
    for {
      enum <- c.downField("enum").as[Option[Seq[String]]]
      default = c
        .downField("default")
        .focus
        .map(
          _.fold(
            None,
            x => Some(x.booleanValue().toString),
            x => Some(x.toBigDecimal.map(_.toString).orElse(x.toLong.map(_.toString)).getOrElse(x.toDouble.toString)),
            x => Some(x),
            _ => None,
            _ => None
          )
        )
        .flatten
    } yield OpenapiServerEnum(enum.getOrElse(Nil), default)
  }
}

case class OpenapiServer(url: String, description: Option[String] = None, variables: Map[String, OpenapiServerEnum] = Map.empty)

object OpenapiServer {
  import io.circe._
  implicit val serverDecoder: Decoder[OpenapiServer] = (c: HCursor) =>
    for {
      url <- c.downField("url").as[String]
      description <- c.downField("description").as[Option[String]]
      variables <- c.downField("variables").as[Option[Map[String, OpenapiServerEnum]]]
    } yield OpenapiServer(url, description, variables.getOrElse(Map.empty))
}
