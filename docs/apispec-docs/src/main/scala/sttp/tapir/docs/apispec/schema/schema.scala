package sttp.tapir.docs.apispec

import sttp.tapir.{SchemaType => TSchemaType, Schema => TSchema}

package object schema {

  val defaultSchemaName: TSchema.SName => String = info => {
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
