package tapir.docs.openapi.schema

import tapir.openapi.{Discriminator, _}
import tapir.{Schema => TSchema}

class DiscriminatorToOpenApi(schemaReferenceMapper: SchemaReferenceMapper) {
  def apply(discriminator: TSchema.Discriminator): Discriminator = {
    val schemas = Some(
      discriminator.mapping.map { case (k, TSchema.SRef(fullName)) => k -> schemaReferenceMapper.map(fullName).$ref }.toListMap
    )
    Discriminator(discriminator.propertyName, schemas)
  }
}
