package sttp.tapir.docs.apispec.schema

import sttp.apispec.{Schema => ASchema, _}
import sttp.tapir.{Schema => TSchema}
import sttp.tapir.internal.IterableToListMap

import scala.collection.immutable.ListMap

case class JsonSchemas(schemas: ListMap[SchemaId, ASchema])

object JsonSchemas {

  def apply(
      schemas: Iterable[TSchema[_]],
      markOptionsAsNullable: Boolean,
      metaSchema: Option[MetaSchema] = None,
      schemaName: TSchema.SName => String = defaultSchemaName
  ): JsonSchemas = {
    val toKeyedSchemas = new ToKeyedSchemas
    val (nonKeyedSchemas, keyedSchemasNested) =
      schemas.partitionMap { topLevelSchema =>
        val asKeyedSchemas = toKeyedSchemas(topLevelSchema)
        if (asKeyedSchemas.isEmpty)
          Left(topLevelSchema)
        else
          Right(ToKeyedSchemas.unique(asKeyedSchemas))
      }
    val keyedSchemas = keyedSchemasNested.flatten
    val keysToIds = calculateUniqueIds(keyedSchemas.map(_._1), (key: SchemaKey) => schemaName(key.name))
    val toSchemaReference = new ToSchemaReference(keysToIds)
    val tschemaToASchema = new TSchemaToASchema(toSchemaReference, markOptionsAsNullable)
    val convertedNonKeyedSchemas: Iterable[(String, ReferenceOr[ASchema])] =
      nonKeyedSchemas.map(s => tschemaToASchema(s)).zipWithIndex.map { case (aSchema, index) =>
        (s"TapirTopLevelRawSchema$index", aSchema)
      }
    val keysToSchemas = keyedSchemas.map(td => (td._1, tschemaToASchema(td._2))).toListMap
    val schemaIds = keysToSchemas.map { case (k, v) => k -> ((keysToIds(k), v)) }

    val allSchemas = (schemaIds.values ++ convertedNonKeyedSchemas)
    JsonSchemas(
      allSchemas.collect { case (k, Right(schema: ASchema)) =>
        (k, schema.copy(`$schema` = metaSchema.map(_.schemaId)))
      }.toListMap
    )
  }
}
