package sttp.tapir.codegen.openapi.models

import OpenapiModels.OpenapiParameter
import cats.implicits.toTraverseOps
import io.circe.Json

case class OpenapiComponent(
    schemas: Map[String, OpenapiSchemaType],
    securitySchemes: Map[String, OpenapiSecuritySchemeType] = Map.empty,
    parameters: Map[String, OpenapiParameter] = Map.empty,
    responses: Map[String, OpenapiResponseDefn] = Map.empty,
    requestBodies: Map[String, OpenapiRequestBody] = Map.empty,
    componentDirectives: Map[String, Json] = Map.empty
)

object OpenapiComponent {
  import io.circe._

  implicit val OpenapiComponentDecoder: Decoder[OpenapiComponent] = { (c: HCursor) =>
    for {
      schemas <- c.getOrElse[Map[String, OpenapiSchemaType]]("schemas")(Map.empty)
      securitySchemes <- c.getOrElse[Map[String, OpenapiSecuritySchemeType]]("securitySchemes")(Map.empty)
      parameters <- c.getOrElse[Map[String, OpenapiParameter]]("parameters")(Map.empty)
      responses <- c.getOrElse[Map[String, OpenapiResponseDefn]]("responses")(Map.empty)
      requestBodies <- c.getOrElse[Map[String, OpenapiRequestBody]]("requestBodies")(Map.empty)
      extensionKeys = c.value.asObject.map(_.keys.toSet).getOrElse(Set.empty[String])
      directives = extensionKeys.toList
        .traverse(key => c.downField(key).as[Option[Json]].toOption.flatten.map(key.stripPrefix("x-") -> _))
        .map(_.toMap)
        .getOrElse(Map.empty)
    } yield {
      OpenapiComponent(
        schemas,
        securitySchemes,
        parameters.map { case (k, v) => s"#/components/parameters/$k" -> v },
        responses,
        requestBodies,
        directives
      )
    }
  }
}
