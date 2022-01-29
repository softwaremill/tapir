package sttp.tapir.codegen.openapi.models

case class OpenapiComponent(schemas: Map[String, OpenapiSchemaType])

object OpenapiComponent {
  import io.circe._

  implicit val OpenapiComponentDecoder: Decoder[OpenapiComponent] = { (c: HCursor) =>
    for {
      schemas <- c.downField("schemas").as[Option[Map[String, OpenapiSchemaType]]]
    } yield {
      OpenapiComponent(schemas.getOrElse(Map.empty))
    }
  }
}
