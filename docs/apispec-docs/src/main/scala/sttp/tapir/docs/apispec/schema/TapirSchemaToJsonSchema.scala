package sttp.tapir.docs.apispec.schema

import sttp.apispec.{Schema => ASchema}
import sttp.tapir.internal.IterableToListMap
import sttp.tapir.{Schema => TSchema}
import scala.collection.immutable.ListMap

object TapirSchemaToJsonSchema {
  def apply(
      schema: TSchema[_],
      markOptionsAsNullable: Boolean,
      addTitleToDefs: Boolean = true,
      metaSchema: MetaSchema = MetaSchemaDraft04,
      schemaName: TSchema.SName => String = defaultSchemaName
  ): ASchema = {

    val asKeyedSchemas = ToKeyedSchemas(schema).drop(1)
    val keyedSchemas = ToKeyedSchemas.uniqueCombined(asKeyedSchemas)

    val keysToIds = calculateUniqueIds(keyedSchemas.map(_._1), (key: SchemaKey) => schemaName(key.name))
    val toSchemaReference = new ToSchemaReference(keysToIds, keyedSchemas.toMap, refRoot = "#/$defs/")
    val tschemaToASchema = new TSchemaToASchema(schemaName, toSchemaReference, markOptionsAsNullable)
    val keysToSchemas = keyedSchemas.map(td => (td._1, tschemaToASchema(td._2, allowReference = false))).toListMap
    val schemaIds = keysToSchemas.map { case (k, v) => k -> ((keysToIds(k), v)) }

    val nestedKeyedSchemas = schemaIds.values
    val rootApiSpecSchemaOrRef: ASchema = tschemaToASchema(schema, allowReference = false)

    val defsList: ListMap[SchemaId, ASchema] =
      nestedKeyedSchemas.collect {
        case (k, nestedSchema: ASchema) if nestedSchema.$ref.isEmpty =>
          (k, nestedSchema.copy(title = nestedSchema.title.orElse(if (addTitleToDefs) Some(k) else None)))
      }.toListMap

    rootApiSpecSchemaOrRef.copy(
      `$schema` = Some(metaSchema.schemaId),
      `$defs` = if (defsList.nonEmpty) Some(defsList) else None
    )
  }
}
