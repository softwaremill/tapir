package sttp.tapir.codegen.openapi.models

import OpenapiModels.OpenapiParameter

case class OpenapiComponent(
    schemas: Map[String, OpenapiSchemaType],
    securitySchemes: Map[String, OpenapiSecuritySchemeType] = Map.empty,
    parameters: Map[String, OpenapiParameter] = Map.empty
)

object OpenapiComponent {
  import io.circe._

  implicit val OpenapiComponentDecoder: Decoder[OpenapiComponent] = { (c: HCursor) =>
    for {
      schemas <- c.getOrElse[Map[String, OpenapiSchemaType]]("schemas")(Map.empty)
      securitySchemes <- c.getOrElse[Map[String, OpenapiSecuritySchemeType]]("securitySchemes")(Map.empty)
      parameters <- c.getOrElse[Map[String, OpenapiParameter]]("parameters")(Map.empty)
    } yield {
      OpenapiComponent(
        schemas,
        securitySchemes,
        parameters.map { case (k, v) => s"#/components/parameters/$k" -> v }
      )
    }
  }
}
