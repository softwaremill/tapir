package sttp.tapir.docs.apispec.schema

import sttp.apispec.{Schema => ASchema}
import sttp.tapir.internal.IterableToListMap
import sttp.tapir.{Schema => TSchema}
import scala.collection.immutable.ListMap
import sttp.tapir.SchemaType

/** Renders json schema from tapir schema.
  *
  * Note [[MetaSchemaDraft04]] is accepted for compatibility, but the [[sttp.apispec.Schema]] produced always follows
  * [[MetaSchemaDraft202012]].
  */
object TapirSchemaToJsonSchema {
  def apply(
      schema: TSchema[_],
      markOptionsAsNullable: Boolean,
      metaSchema: MetaSchema = MetaSchemaDraft202012,
      schemaName: TSchema.SName => String = defaultSchemaName
  ): ASchema = {

    var asKeyedSchemas = ToKeyedSchemas(schema)
    // The schemas in `asKeyedSchemas` will be part of `defs`. If the top-level schema is not an array), it should
    // be rendered at the top-level, not through a def - then, dropping the first schema from definitions.
    if (!schema.schemaType.isInstanceOf[SchemaType.SArray[_, _]]) {
      asKeyedSchemas = asKeyedSchemas.drop(1)
    }

    val keyedSchemas = ToKeyedSchemas.uniqueCombined(asKeyedSchemas)

    val keysToIds = calculateUniqueIds(keyedSchemas.map(_._1), (key: SchemaKey) => schemaName(key.name))
    val toSchemaReference = new ToSchemaReference(keysToIds, keyedSchemas.toMap, refRoot = "#/$defs/")
    val tschemaToASchema = new TSchemaToASchema(schemaName, toSchemaReference, markOptionsAsNullable)
    val keysToSchemas = keyedSchemas.map(td => (td._1, tschemaToASchema(td._2, allowReference = false))).toListMap
    val schemaIds = keysToSchemas.map { case (k, v) => k -> ((keysToIds(k), v)) }

    val defsList = schemaIds.values.toListMap
    val rootApiSpecSchemaOrRef: ASchema = tschemaToASchema(schema, allowReference = false)

    rootApiSpecSchemaOrRef.copy(
      `$schema` = Some(metaSchema.schemaId),
      `$defs` = if (defsList.nonEmpty) Some(defsList) else None
    )
  }

  // binary compatibility shim
  private[docs] def apply(
      schema: TSchema[_],
      markOptionsAsNullable: Boolean,
      addTitleToDefs: Boolean,
      metaSchema: MetaSchema,
      schemaName: TSchema.SName => String
  ): ASchema = apply(schema, markOptionsAsNullable, metaSchema, schemaName)
}
