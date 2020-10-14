package sttp.tapir.docs.openapi.schema

import sttp.tapir.apispec._
import sttp.tapir.{SchemaType => TSchemaType}

class DiscriminatorToOpenAPI(schemaReferenceMapper: SchemaReferenceMapper) {
  def apply(discriminator: TSchemaType.Discriminator): Discriminator = {
    val schemas = Some(
      discriminator.mappingOverride.map { case (k, TSchemaType.SRef(fullName)) => k -> schemaReferenceMapper.map(fullName).$ref }.toListMap
    )
    Discriminator(discriminator.propertyName, schemas)
  }
}
