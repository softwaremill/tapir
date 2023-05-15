package sttp.tapir.docs.apispec.schema

import sttp.apispec.{Schema => ASchema}
import sttp.tapir.internal.IterableToListMap
import sttp.tapir.{Schema => TSchema}
import scala.collection.immutable.ListMap

object JsonSchemas {

  private val toKeyedSchemas = new ToKeyedSchemas

  def apply(
      schema: TSchema[_],
      markOptionsAsNullable: Boolean,
      metaSchema: MetaSchema = MetaSchemaDraft04,
      schemaName: TSchema.SName => String = defaultSchemaName
  ): ASchema = {

    val asKeyedSchemas = toKeyedSchemas(schema).drop(1)
    val keyedSchemas = ToKeyedSchemas.unique(asKeyedSchemas)

    val keysToIds = calculateUniqueIds(keyedSchemas.map(_._1), (key: SchemaKey) => schemaName(key.name))
    val toSchemaReference = new ToSchemaReference(keysToIds, refRoot = "#/$defs/")
    val tschemaToASchema = new TSchemaToASchema(toSchemaReference, markOptionsAsNullable)
    val keysToSchemas = keyedSchemas.map(td => (td._1, tschemaToASchema(td._2))).toListMap
    val schemaIds = keysToSchemas.map { case (k, v) => k -> ((keysToIds(k), v)) }

    val nestedKeyedSchemas = (schemaIds.values)
    // TODO proper handling of ref input schema
    val rootApiSpecSchema: ASchema = tschemaToASchema(schema) match {
      case Right(apiSpecSchema) => apiSpecSchema
      case Left(_) => throw new IllegalArgumentException(s"Input schema $schema is a ref")
    }
    val defsList: ListMap[SchemaId, ASchema] =
      nestedKeyedSchemas.collect { case (k, Right(nestedSchema: ASchema)) =>
        (k, nestedSchema)
      }.toListMap

    rootApiSpecSchema.copy(
      `$schema` = Some(metaSchema.schemaId),
      `$defs` = if (defsList.nonEmpty) Some(defsList) else None
  )
  }
}
