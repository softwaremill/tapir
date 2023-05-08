package sttp.tapir.docs.apispec.schema

import sttp.apispec.{Schema => ASchema, _}
import sttp.tapir.{Schema => TSchema}
import sttp.tapir.internal.IterableToListMap

import scala.collection.immutable.ListMap

object TapirSchemaToJsonSchema {

  def apply(
      schemas: Iterable[TSchema[_]],
      markOptionsAsNullable: Boolean,
      schemaName: TSchema.SName => String = defaultSchemaName
  ): ListMap[SchemaId, ReferenceOr[ASchema]] = {
    val toKeyedSchemas = new ToKeyedSchemas
    val keyedSchemas = ToKeyedSchemas.unique(
      schemas.flatMap(schema => toKeyedSchemas(schema))
    )
    val keysToIds = calculateUniqueIds(keyedSchemas.map(_._1), (key: SchemaKey) => schemaName(key.name))
    val toSchemaReference = new ToSchemaReference(keysToIds)
    val tschemaToASchema = new TSchemaToASchema(toSchemaReference, markOptionsAsNullable)
    val keysToSchemas = keyedSchemas.map(td => (td._1, tschemaToASchema(td._2))).toListMap

    val schemaIds = keysToSchemas.map { case (k, v) => k -> ((keysToIds(k), v)) }
    (schemaIds.values.toListMap)
  }
}
