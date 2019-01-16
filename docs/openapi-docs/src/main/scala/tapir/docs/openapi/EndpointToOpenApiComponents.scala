package tapir.docs.openapi
import tapir.docs.openapi.ObjectSchemasForEndpoints.SchemaKeys
import tapir.openapi.Components

private[openapi] class EndpointToOpenApiComponents(schemaKeys: SchemaKeys) {

  def components: Option[Components] = {
    if (schemaKeys.nonEmpty) {
      Some(Components(Some(schemaKeys.map {
        case (_, (key, Right(schema))) =>
          key -> Right(schema)
      })))
    } else None
  }
}
