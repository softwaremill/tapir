package tapir.docs.openapi

import tapir.docs.openapi.schema.ObjectSchemas
import tapir.openapi.Components

private[openapi] class EndpointToOpenApiComponents(objectSchemas: ObjectSchemas, securitySchemes: SecuritySchemes) {
  def components: Option[Components] = {
    val keyToSchema = objectSchemas.keyToOSchema
    if (keyToSchema.nonEmpty || securitySchemes.nonEmpty) Some(Components(keyToSchema, securitySchemes.values.toMap.mapValues(Right(_))))
    else None
  }
}
