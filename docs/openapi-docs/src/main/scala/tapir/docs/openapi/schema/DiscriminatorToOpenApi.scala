package tapir.docs.openapi.schema

import tapir.Schema.SObject
import tapir.openapi.{Discriminator, _}
import tapir.{Schema => TSchema}

class DiscriminatorToOpenApi(schemaReferenceMapper: SchemaReferenceMapper) {
  def apply(discriminator: TSchema.Discriminator[_]): Discriminator = {
    val schemas = Some(
      discriminator.mapping.map { case (k, SObject(i, _, _)) => k -> schemaReferenceMapper.map(i.fullName).$ref }.toListMap)
    Discriminator(discriminator.propertyName, schemas)
  }
}
