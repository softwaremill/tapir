package sttp.tapir.docs.apispec.schema

import sttp.tapir.apispec._
import sttp.tapir.{SchemaType => TSchemaType}

private[schema] class TDiscriminatorToADiscriminator(schemaReferenceMapper: SchemaReferenceMapper) {
  def apply(discriminator: TSchemaType.Discriminator): Discriminator = {
    val schemas = Some(
      discriminator.mappingOverride.map { case (k, TSchemaType.SRef(fullName)) => k -> schemaReferenceMapper.map(fullName).$ref }.toListMap
    )
    Discriminator(discriminator.propertyName, schemas)
  }
}
