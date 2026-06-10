package sttp.tapir.docs.apispec.schema

import sttp.apispec.{Schema => ASchema}
import sttp.tapir.internal.IterableToListMap
import sttp.tapir.{Schema => TSchema}
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
      failOnDuplicateSchemaName: Boolean = false,
      metaSchema: MetaSchema = MetaSchemaDraft202012,
      schemaName: TSchema.SName => String = defaultSchemaName
  ): ASchema = {

    var asKeyedSchemas = ToKeyedSchemas(schema)
    val rootRecursive = isRootRecursive(schema)

    // The schemas in `asKeyedSchemas` will be part of `defs` in the generated JSON Schema. The first schema should be
    // part of the defs if:
    // - the top-level schema is an array
    // - the top-level schema is referenced in the schema itself (recursive)
    // In other cases, dropping the first schema, as it will be rendered at the top-level instead.
    if (!schema.schemaType.isInstanceOf[SchemaType.SArray[_, _]] && !rootRecursive) {
      asKeyedSchemas = asKeyedSchemas.drop(1)
    }

    val keyedSchemas = ToKeyedSchemas.uniqueCombined(asKeyedSchemas)

    val keysToIds = calculateUniqueIds(keyedSchemas.map(_._1), (key: SchemaKey) => schemaName(key.name), failOnDuplicateSchemaName)
    val toSchemaReference = new ToSchemaReference(keysToIds, keyedSchemas.toMap, refRoot = "#/$defs/")
    val tschemaToASchema = new TSchemaToASchema(schemaName, toSchemaReference, markOptionsAsNullable)
    val keysToSchemas = keyedSchemas.map(td => (td._1, tschemaToASchema(td._2, allowReference = false))).toListMap
    val schemaIds = keysToSchemas.map { case (k, v) => k -> ((keysToIds(k), v)) }

    val defsList = schemaIds.values.toListMap
    val rootApiSpecSchemaOrRef: ASchema = tschemaToASchema(schema, allowReference = rootRecursive)

    rootApiSpecSchemaOrRef.copy(
      `$schema` = Some(metaSchema.schemaId),
      `$defs` = if (defsList.nonEmpty) Some(defsList) else None
    )
  }

  /** @return true iff there is a reference ([[SchemaType.SRef]]) to the root schema in the schema itself. */
  private def isRootRecursive(schema: TSchema[_]): Boolean = schema.name.exists(hasReferenceTo(schema, _))

  private def hasReferenceTo(schema: TSchema[_], name: TSchema.SName): Boolean = {
    schema.schemaType match {
      case SchemaType.SProduct(fields)            => fields.exists(f => hasReferenceTo(f.schema, name))
      case SchemaType.SCoproduct(subtypes, _)     => subtypes.exists(s => hasReferenceTo(s, name))
      case SchemaType.SArray(element)             => hasReferenceTo(element, name)
      case SchemaType.SOption(element)            => hasReferenceTo(element, name)
      case SchemaType.SOpenProduct(fields, value) => fields.exists(f => hasReferenceTo(f.schema, name)) || hasReferenceTo(value, name)
      case SchemaType.SRef(name2)                 => name == name2
      case _                                      => false
    }
  }
}
