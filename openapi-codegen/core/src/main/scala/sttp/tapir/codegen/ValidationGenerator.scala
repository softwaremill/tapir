package sttp.tapir.codegen

import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiDocument
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{OpenapiSchemaRef, OpenapiSchemaString}
import sttp.tapir.codegen.util.JavaEscape

object ValidationGenerator {

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
      def allowNull(s: String) = if (required) s
      else s"Validator.custom[Option[String]](ot => ot.map($s(_)).map{ case Nil => ValidationResult.Valid ; case l => ValidationResult.Invalid()}.getOrElse(ValidationResult.Valid))"
      validations match {
        case Nil      => ""
        case h +: Nil => s".validate(${allowNull(h)})"
        case seq      => s""".validate(${allowNull(s"""Validator.all(${seq.mkString(", ")})""")})"""
      }
    case _ => ""
  }
}
