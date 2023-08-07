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
      parameters <- c.downField("parameters").as[Option[Map[String, OpenapiParameter]]].map(_.getOrElse(Map.empty))
    } yield {
      OpenapiComponent(schemas, parameters.map { case (k, v) => s"#/components/parameters/$k" -> v })
    }
  }
}
