package sttp.tapir.codegen.openapi.models

import OpenapiModels.{OpenapiHeader, OpenapiParameter}

case class OpenapiComponent(
    schemas: Map[String, OpenapiSchemaType],
    securitySchemes: Map[String, OpenapiSecuritySchemeType] = Map.empty,
    parameters: Map[String, OpenapiParameter] = Map.empty,
    responses: Map[String, OpenapiResponseDefn] = Map.empty,
    requestBodies: Map[String, OpenapiRequestBody] = Map.empty,
    headers: Map[String, OpenapiHeader] = Map.empty
)

object OpenapiComponent {
  import io.circe._
  import NameValidation._
  import cats.implicits._

  implicit val OpenapiComponentDecoder: Decoder[OpenapiComponent] = { (c: HCursor) =>
    for {
      schemas <- c.getOrElse[Map[String, OpenapiSchemaType]]("schemas")(Map.empty)
      nonMatching = schemas.keySet.filter(!_.matches(validName))
      _ <- Right(()).ensure(
        DecodingFailure(s"Schema names ${nonMatching} do not match expected regex! Expecting legal scala type names", c.history)
      )(_ => nonMatching.isEmpty)
      securitySchemes <- c.getOrElse[Map[String, OpenapiSecuritySchemeType]]("securitySchemes")(Map.empty)
      parameters <- c.getOrElse[Map[String, OpenapiParameter]]("parameters")(Map.empty)
      responses <- c.getOrElse[Map[String, OpenapiResponseDefn]]("responses")(Map.empty)
      requestBodies <- c.getOrElse[Map[String, OpenapiRequestBody]]("requestBodies")(Map.empty)
      headers <- c.getOrElse[Map[String, OpenapiHeader]]("headers")(Map.empty)
    } yield {
      OpenapiComponent(
        schemas,
        securitySchemes,
        parameters.map { case (k, v) => s"#/components/parameters/$k" -> v },
        responses,
        requestBodies,
        // re-keyed by full ref, exactly like `parameters` above, so that OpenapiHeaderRef.resolved can look up by `$ref.name`
        headers.map { case (k, v) => s"#/components/headers/$k" -> v }
      )
    }
  }
}
