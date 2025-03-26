package sttp.tapir.codegen.openapi.models

import sttp.tapir.codegen.openapi.models.OpenapiModels.{OpenapiHeader, OpenapiRequestBodyContent, OpenapiResponseContent}
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{OpenapiSchemaRef, OpenapiSchemaSimpleType}

case class OpenapiResponseDefn(
    description: String,
    headers: Map[String, OpenapiHeader],
    content: Seq[OpenapiResponseContent]
)

object OpenapiResponseDefn {
  import io.circe._
  implicit val OpenapiResponseDefnDecoder: Decoder[OpenapiResponseDefn] = { (c: HCursor) =>
    for {
      description <- c.downField("description").as[String]
      headers <- c.getOrElse[Map[String, OpenapiHeader]]("headers")(Map.empty)
      content <- c.getOrElse[Seq[OpenapiResponseContent]]("content")(Nil)
    } yield OpenapiResponseDefn(description, headers, content)
  }
}

case class OpenapiRequestBody(
    description: String,
    content: Seq[OpenapiRequestBodyContent],
    required: Boolean
)

object OpenapiRequestBody {
  import io.circe._
  implicit val OpenapiRequestBodyDecoder: Decoder[OpenapiRequestBody] = { (c: HCursor) =>
    for {
      description <- c.downField("description").as[String]
      content <- c.getOrElse[Seq[OpenapiRequestBodyContent]]("content")(Nil)
      required <- c.getOrElse[Boolean]("required")(false)
    } yield OpenapiRequestBody(description, content, required)
  }
}
