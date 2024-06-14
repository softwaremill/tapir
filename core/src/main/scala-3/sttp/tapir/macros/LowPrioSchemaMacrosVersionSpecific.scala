package sttp.tapir
package macros

import sttp.tapir.Schema.SName

import scala.compiletime.*
import scala.compiletime.ops.any.IsConst

trait LowPrioSchemaMacrosVersionSpecific:
  inline given derivedStringBasedUnionEnumeration[S](using IsUnionOf[String, S]): Schema[S] =
    val values = UnionDerivation.constValueUnionTuple[String, S]
    Schema
      .string[S]
      .name(SName(values.toList.mkString("_or_")))
      .validate(Validator.enumeration(values.toList.asInstanceOf[List[S]]))

  inline given constStringToEnum[S <: String](using IsConst[S] =:= true): Schema[S] =
    Schema
      .string[S]
      .name(SName(constValue[S]))
      .validate(Validator.enumeration(List(constValue[S]).asInstanceOf[List[S]]))
