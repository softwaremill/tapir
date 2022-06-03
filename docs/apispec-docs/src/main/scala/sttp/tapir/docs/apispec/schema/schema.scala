package sttp.tapir.docs.apispec

import sttp.tapir.{SchemaType => TSchemaType, Schema => TSchema}

package object schema {
  private[docs] type NamedSchema = (TSchema.SName, TSchema[_])
  private[docs] type ObjectKey = String

  private[docs] def calculateUniqueKeys[T](ts: Iterable[T], toName: T => String): Map[T, String] = {
    case class Assigment(nameToT: Map[String, T], tToKey: Map[T, String])
    ts
      .foldLeft(Assigment(Map.empty, Map.empty)) { case (Assigment(nameToT, tToKey), t) =>
        val key = uniqueName(toName(t), n => !nameToT.contains(n) || nameToT.get(n).contains(t))

        Assigment(
          nameToT + (key -> t),
          tToKey + (t -> key)
        )
      }
      .tToKey
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
