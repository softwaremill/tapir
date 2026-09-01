package sttp.tapir.docs.apispec

import sttp.tapir.{SchemaType => TSchemaType, Schema => TSchema}

package object schema {

  private[docs] val defaultSchemaName: TSchema.SName => String = info => {
    def prepareName(name: String) = name.split('.').last
    (info.fullName +: info.typeParameterShortNames).map(prepareName).mkString("_")
  }

  /*
  SchemaId:  Used in the documentation to identify a schema in the components section, and to use in in references.
             Typically contains the type name, possibly with a suffix for disambiguation.
  SchemaKey: Used as a key to differentiate between schemas when collecting all used schemas within an endpoint.
             Each schema key corresponds to an entry in the components section.
  SName:     The name of the class + type parameters associated with a schema.
   */

  private[docs] type KeyedSchema = (SchemaKey, TSchema[_])
  private[docs] type SchemaId = String

  private[docs] val defaultDuplicateSchemaNameError: List[String] => String = baseNames =>
    s"Duplicate schema names found: ${baseNames.mkString(", ")}. " +
      "Consider using unique class names or customize the schemaName function."

  private[docs] def calculateUniqueIds[T](
      ts: Iterable[T],
      toIdBase: T => String,
      failOnDuplicateName: Boolean,
      duplicateNameError: List[String] => String = defaultDuplicateSchemaNameError
  ): Map[T, String] = {
    case class Assignment(idToT: Map[String, T], tToId: Map[T, String])
    val result = ts
      .foldLeft(Assignment(Map.empty, Map.empty)) { case (Assignment(idToT, tToId), t) =>
        val id = uniqueString(toIdBase(t), n => !idToT.contains(n) || idToT.get(n).contains(t))

        Assignment(
          idToT + (id -> t),
          tToId + (t -> id)
        )
      }

    if (failOnDuplicateName) {
      val conflicts: Map[T, String] = result.tToId.collect { case (t, id) if toIdBase(t) != id => t -> id }

      if (conflicts.nonEmpty) {
        // Extract unique base names that had conflicts
        val baseNames = conflicts.map { case (t, _) => toIdBase(t) }.toSet.toList.sorted
        throw new IllegalStateException(duplicateNameError(baseNames))
      }
    }

    result.tToId
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
