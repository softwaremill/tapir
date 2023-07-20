package sttp.tapir.codegen.openapi.models

case class OpenapiComponent(schemas: Map[String, OpenapiSchemaType])

object OpenapiComponent {
  import io.circe._

  implicit val OpenapiComponentDecoder: Decoder[Option[OpenapiComponent]] = { (c: HCursor) =>
    for {
      schemas <- c.downField("schemas").as[Option[Map[String, OpenapiSchemaType]]]
    } yield {
      schemas match {
        case None    => None
        case Some(m) => Some(OpenapiComponent(m))
      }
    }
  }
}
