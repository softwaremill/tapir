package sttp.tapir.docs.apispec

import sttp.apispec.{Schema => ASchema, _}
import sttp.tapir.{SchemaType => TSchemaType, Schema => TSchema}
import sttp.tapir.internal.IterableToListMap
import scala.collection.immutable.ListMap

package object schema {

  def fromTapirSchemas(
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

  private[docs] val defaultSchemaName: TSchema.SName => String = info => {
    val shortName = info.fullName.split('.').last
    (shortName +: info.typeParameterShortNames).mkString("_")
  }
  /*
  SchemaId - used in the documentation to identify a schema in the components section, and to use in in references
  SchemaKey - used as a key to differentiate between schemas when collecting all used schemas within an endpoint
  SName - the name of the class + type parameters associated with a schema
   */

  private[docs] type KeyedSchema = (SchemaKey, TSchema[_])
  private[docs] type SchemaId = String

  private[docs] def calculateUniqueIds[T](ts: Iterable[T], toIdBase: T => String): Map[T, String] = {
    case class Assigment(idToT: Map[String, T], tToId: Map[T, String])
    ts
      .foldLeft(Assigment(Map.empty, Map.empty)) { case (Assigment(idToT, tToId), t) =>
        val id = uniqueString(toIdBase(t), n => !idToT.contains(n) || idToT.get(n).contains(t))

        Assigment(
          idToT + (id -> t),
          tToId + (t -> id)
        )
      }
      .tToId
  }

  private[docs] def propagateMetadataForOption[T, E](schema: TSchema[T], opt: TSchemaType.SOption[T, E]): TSchemaType.SOption[T, E] = {
    opt.copy(
      element = opt.element.copy(
        description = schema.description.orElse(opt.element.description),
        format = schema.format.orElse(opt.element.format),
        deprecated = schema.deprecated || opt.element.deprecated,
        encodedExample = schema.encodedExample.orElse(opt.element.encodedExample),
        default = schema.default.flatMap { case (t, raw) => opt.toOption(t).map((_, raw)) }.orElse(opt.element.default)
      )
    )(opt.toOption)
  }
}
