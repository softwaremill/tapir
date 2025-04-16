package sttp.tapir.codegen

import io.circe.JsonNumber
import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiDocument
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{
  OpenapiSchemaArray,
  OpenapiSchemaNumericType,
  OpenapiSchemaRef,
  OpenapiSchemaString
}
import sttp.tapir.codegen.util.JavaEscape

object ValidationGenerator {
  private def render(n: JsonNumber): String = n.toLong.map(_.toString).getOrElse(n.toDouble.toString)
  private def allowNull(tpe: String, required: Boolean)(s: String): String = if (required) s
  else
    s"Validator.custom[Option[$tpe]](ot => ot.map($s(_)).map{ case Nil => ValidationResult.Valid ; case l => ValidationResult.Invalid()}.getOrElse(ValidationResult.Valid))"

  def mkValidations(doc: OpenapiDocument, t: OpenapiSchemaType, required: Boolean): String = t match {
    case r: OpenapiSchemaRef =>
      doc.components
        .flatMap(_.schemas.get(r.stripped))
        .map(t => mkValidations(doc, t, required && !t.nullable))
        .getOrElse("")
    case OpenapiSchemaString(_, p, mi, ma) =>
      val validations = p.map(s => s"""Validator.pattern("${JavaEscape.escapeString(s)}")""").toSeq ++
        mi.map(s => s"""Validator.minLength($s)""") ++
        ma.map(s => s"""Validator.maxLength($s)""")
      validations match {
        case Nil      => ""
        case h +: Nil => s".validate(${allowNull("String", required)(h)})"
        case seq      => s""".validate(${allowNull("String", required)(s"""Validator.all(${seq.mkString(", ")})""")})"""
      }
    case numeric: OpenapiSchemaNumericType =>
      val restrictions = numeric.restrictions
      val validations =
        restrictions.min
          .map(s => s"""Validator.min(${render(s)}, exclusive = ${restrictions.exclusiveMinimum.getOrElse(false)})""")
          .toSeq ++
          restrictions.max.map(s => s"""Validator.max(${render(s)}, exclusive = ${restrictions.exclusiveMaximum.getOrElse(false)})""") ++
          restrictions.multipleOf
            .flatMap(_.toLong)
            .map(v => s"Validator.custom(v => if (v % $v == 0) ValidationResult.Valid else ValidationResult.Invalid)")
      validations match {
        case Nil      => ""
        case h +: Nil => s".validate(${allowNull(numeric.scalaType, required)(h)})"
        case seq      => s""".validate(${allowNull(numeric.scalaType, required)(s"""Validator.all(${seq.mkString(", ")})""")})"""
      }
    case OpenapiSchemaArray(_, _, _, restrictions) =>
      val validations =
        restrictions.minItems.map(s => s"""Validator.minSize($s)""").toSeq ++
          restrictions.maxItems.map(s => s"""Validator.maxSize($s)""").toSeq
      validations match {
        case Nil      => ""
        case h +: Nil => s".validate(${allowNull("Iterable[?]", required)(h)})"
        case seq      => s""".validate(${allowNull("Iterable[?]", required)(s"""Validator.all(${seq.mkString(", ")})""")})"""
      }
    case _ => ""
  }
}
