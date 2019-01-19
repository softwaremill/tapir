package tapir.docs.openapi

import tapir.docs.openapi.schema.ObjectSchemas
import tapir.openapi.Components

private[openapi] class EndpointToOpenApiComponents(objectSchemas: ObjectSchemas) {
  def components: Option[Components] = {
    val keyToSchema = objectSchemas.keyToOSchema
    if (keyToSchema.nonEmpty) Some(Components(Some(keyToSchema)))
    else None
  }
}
