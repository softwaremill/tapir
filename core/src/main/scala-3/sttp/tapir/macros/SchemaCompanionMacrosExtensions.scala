package sttp.tapir
package macros

import sttp.tapir.Schema.SName

object SchemaCompanionMacrosExtensions extends SchemaCompanionMacrosExtensions

trait SchemaCompanionMacrosExtensions:
  inline given derivedStringBasedUnionEnumeration[S](using IsUnionOf[String, S]): Schema[S] =
    lazy val validator = Validator.derivedStringBasedUnionEnumeration[S]
    Schema
      .string[S]
      .name(SName(validator.possibleValues.toList.mkString("_or_")))
      .validate(validator)
