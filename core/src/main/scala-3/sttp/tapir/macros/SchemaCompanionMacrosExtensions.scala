package sttp.tapir
package macros

import sttp.tapir.Schema.SName

object SchemaCompanionMacrosExtensions extends SchemaCompanionMacrosExtensions

trait SchemaCompanionMacrosExtensions:
  inline given derivedStringBasedUnionEnumeration[S](using IsUnionOf[String, S]): Schema[S] =
    lazy val values = UnionDerivation.constValueUnionTuple[String, S]
    lazy val validator = Validator.enumeration(values.toList.asInstanceOf[List[S]])
    Schema
      .string[S]
      .name(SName(values.toList.mkString("_or_")))
      .validate(validator)
