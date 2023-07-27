package sttp.tapir.codegen.openapi.models

import cats.syntax.either._

import OpenapiModels.OpenapiParameter

case class OpenapiComponent(
    schemas: Map[String, OpenapiSchemaType],
    parameters: Map[String, OpenapiParameter] = Map.empty
)

object OpenapiComponent {
  import io.circe._

  implicit val OpenapiComponentDecoder: Decoder[OpenapiComponent] = { (c: HCursor) =>
    for {
      schemas <- c.downField("schemas").as[Map[String, OpenapiSchemaType]]
      parameters <- c.downField("parameters").as[Map[String, OpenapiParameter]].orElse(Right(Map.empty[String, OpenapiParameter]))
    } yield {
      OpenapiComponent(schemas, parameters.map { case (k, v) => s"#/components/parameters/$k" -> v })
    }
  }
}
