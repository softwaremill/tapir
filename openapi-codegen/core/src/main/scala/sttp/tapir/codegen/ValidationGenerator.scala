package sttp.tapir.codegen

import io.circe.JsonNumber
import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiDocument
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{OpenapiSchemaArray, OpenapiSchemaMap, OpenapiSchemaNumericType, OpenapiSchemaObject, OpenapiSchemaRef, OpenapiSchemaString}
import sttp.tapir.codegen.util.JavaEscape

case class ValidationDefn(name: String, tpe: String, construct: ValidationDefns => Option[String])
case class ValidationDefns(defns: Map[String, ValidationDefn]) {
  def render: String = defns.values
    .flatMap { d => d.construct(this).map(impl => s"lazy val ${d.name}Validator: Validator[${d.tpe}] = $impl") }
    .mkString("\n")
}
object ValidationDefns {
  val empty: ValidationDefns = ValidationDefns(Map.empty)
}

object ValidationGenerator {
  private def render(n: JsonNumber): String = n.toLong.map(_.toString).getOrElse(n.toDouble.toString)
  def allowNull(tpe: String, required: Boolean)(s: String): String = if (required) s
  else
    s"""Validator.custom[Option[$tpe]](ot => ot.map($s(_)).map{
       |  case Nil => ValidationResult.Valid
       |  case l   => ValidationResult.Invalid(l.flatMap(_.customMessage).toList)
       |}.getOrElse(ValidationResult.Valid))""".stripMargin
  private def opt(t: String, nullable: Boolean) = if (nullable) s"Option[$t]" else t
  private def singleton(name: String, tpe: String, validationDefn: String): Seq[ValidationDefn] =
    Seq(ValidationDefn(name, tpe, _ => Some(validationDefn)))

  def mkValidators(doc: OpenapiDocument): ValidationDefns =
    doc.components.map(_.schemas).map { schemas =>
      val mapped = schemas.flatMap { case (k, v) => genValidationDefn(schemas)(k, v) }
      if (mapped.isEmpty) ValidationDefns.empty
      else ValidationDefns(mapped.map(e => e.name -> e).toMap)
    }.getOrElse(ValidationDefns.empty)
  def genValidationDefn(schemas: Map[String, OpenapiSchemaType])(name: String, schema: OpenapiSchemaType): Seq[ValidationDefn] =
    schema match {
      case r: OpenapiSchemaRef => schemas.get(r.stripped).toSeq.flatMap(s => genValidationDefn(schemas)(r.stripped, s))
      case OpenapiSchemaString(nullable, p, mi, ma) =>
        val validations: Seq[String] =
          p.map(s => s"""Validator.pattern("${JavaEscape.escapeString(s)}")""").toSeq ++
            mi.map(s => s"""Validator.minLength($s)""") ++
            ma.map(s => s"""Validator.maxLength($s)""")
        validations match {
          case Nil                => Nil
          case (h: String) +: Nil => singleton(name, opt("String", nullable), allowNull("String", !nullable)(h))
          case seq => singleton(name, opt("String", nullable), allowNull("String", !nullable)(s"""Validator.all(${seq.mkString(", ")})"""))
        }
      case numeric: OpenapiSchemaNumericType =>
        val restrictions = numeric.restrictions
        val validations: Seq[String] =
          restrictions.min
            .map(s => s"""Validator.min(${render(s)}, exclusive = ${restrictions.exclusiveMinimum.getOrElse(false)})""")
            .toSeq ++
            restrictions.max.map(s => s"""Validator.max(${render(s)}, exclusive = ${restrictions.exclusiveMaximum.getOrElse(false)})""") ++
            restrictions.multipleOf
              .flatMap(_.toLong)
              .map(v => s"Validator.custom(v => if (v % $v == 0) ValidationResult.Valid else ValidationResult.Invalid)")
        val nullable = numeric.nullable
        val scalaType = numeric.scalaType
        validations match {
          case Nil                => Nil
          case (h: String) +: Nil => singleton(name, opt(scalaType, nullable), allowNull(scalaType, !nullable)(h))
          case seq =>
            singleton(name, opt(scalaType, nullable), allowNull(scalaType, !nullable)(s"""Validator.all(${seq.mkString(", ")})"""))
        }
      case OpenapiSchemaArray(t, nb, _, restrictions) =>
        val rawTpeName = name.capitalize
        val tpeName = opt(rawTpeName, nb)
        val validatedElemRefs: Seq[String] = genValidationDefn(schemas)(s"${name}Item", t).map(_.name)
        val validations: Seq[String] =
          restrictions.minItems.map(s => s"""Validator.minSize($s)""").toSeq ++
            restrictions.maxItems.map(s => s"""Validator.maxSize($s)""").toSeq
        def mkItemValidation(validatorName: String) =
          s"""Validator.custom(
             |  (_: $rawTpeName).map($validatorName.apply).zipWithIndex.flatMap { case (l, i) => l.map(_ -> i) } match {
             |    case Nil => ValidationResult.Valid
             |    case errs =>
             |      val msgs: List[String] = "Array item validation failed for $rawTpeName" +: errs.map { case (err, idx) =>
             |        s"Element $$idx is invalid$${err.customMessage.map(" because: " + _).getOrElse("")}"
             |      }.toList
             |      ValidationResult.Invalid(msgs)
             |  }
             |)""".stripMargin
        val maybeItemValidation = validatedElemRefs match {
          case Nil    => None
          case h +: _ => Some(mkItemValidation(h + "Validator"))
        }
        (validations, maybeItemValidation) match {
          case (Nil, None)                => Nil
          case (Nil, Some(v: String))     => singleton(name, tpeName, allowNull(rawTpeName, !nb)(v))
          case ((h: String) +: Nil, None) => singleton(name, tpeName, allowNull(rawTpeName, !nb)(h))
          case (seq, maybeItem) =>
            singleton(name, tpeName, allowNull(rawTpeName, !nb)(s"""Validator.all(${(seq ++ maybeItem).mkString(", ")})"""))
        }
      case OpenapiSchemaMap(t, nb, restrictions) =>
        val rawTpeName = name.capitalize
        val tpeName = opt(rawTpeName, nb)
        val validatedElemRefs: Seq[String] = genValidationDefn(schemas)(s"${name}Item", t).map(_.name)
        val validations: Seq[String] =
          restrictions.minProperties.map(s => s"""Validator.minSize($s)""").toSeq ++
            restrictions.maxProperties.map(s => s"""Validator.maxSize($s)""").toSeq
        def mkElemValidation(validatorName: String) =
          s"""Validator.custom(
             |  (_: $rawTpeName).map{ case (k, v) => k -> $validatorName.apply(v) }.flatMap { case (k, l) => l.map(k -> _) } match {
             |    case Nil => ValidationResult.Valid
             |    case errs =>
             |      val msgs: List[String] = "Map element validation failed for $rawTpeName" +: errs.map { case (k, err) =>
             |        s"Entry $$k is invalid$${err.customMessage.map(" because: " + _).getOrElse("")}"
             |      }.toList
             |      ValidationResult.Invalid(msgs)
             |  }
             |)""".stripMargin
        val maybeItemValidation = validatedElemRefs match {
          case Nil    => None
          case h +: _ => Some(mkElemValidation(h + "Validator"))
        }
        (validations, maybeItemValidation) match {
          case (Nil, None)                => Nil
          case (Nil, Some(v: String))     => singleton(name, tpeName, allowNull(rawTpeName, !nb)(v))
          case ((h: String) +: Nil, None) => singleton(name, tpeName, allowNull(rawTpeName, !nb)(h))
          case (seq, maybeItem) =>
            singleton(name, tpeName, allowNull(rawTpeName, !nb)(s"""Validator.all(${(seq ++ maybeItem).mkString(", ")})"""))
        }
//      case OpenapiSchemaObject(ts, rs, nb, _) =>
//        val rawTpeName = name.capitalize
//        val tpeName = opt(rawTpeName, nb)
//        val validatedElemRefs: Seq[String] = genValidationDefn(schemas)(s"${name}Item", t).map(_.name)
//        def mkElemValidation(fname: String, validatorName: String) =
//          s"""Validator.custom(
//             |  (_: $rawTpeName).map{ case (k, v) => k -> $validatorName.apply(v) }.flatMap { case (k, l) => l.map(k -> _) } match {
//             |    case Nil => ValidationResult.Valid
//             |    case errs =>
//             |      val msgs: List[String] = "Map element validation failed for $rawTpeName" +: errs.map { case (k, err) =>
//             |        s"Entry $$k is invalid$${err.customMessage.map(" because: " + _).getOrElse("")}"
//             |      }.toList
//             |      ValidationResult.Invalid(msgs)
//             |  }
//             |)""".stripMargin
//        val maybeItemValidation = validatedElemRefs match {
//          case Nil    => None
//          case h +: _ => Some(mkElemValidation(h + "Validator"))
//        }
//        (validations, maybeItemValidation) match {
//          case (Nil, None)                => Nil
//          case (Nil, Some(v: String))     => singleton(name, tpeName, allowNull(rawTpeName, !nb)(v))
//          case ((h: String) +: Nil, None) => singleton(name, tpeName, allowNull(rawTpeName, !nb)(h))
//          case (seq, maybeItem) =>
//            singleton(name, tpeName, allowNull(rawTpeName, !nb)(s"""Validator.all(${(seq ++ maybeItem).mkString(", ")})"""))
//        }
      case _ => Nil
    }

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
    case OpenapiSchemaMap(_, _, restrictions) =>
      val validations =
        restrictions.minProperties.map(s => s"""Validator.minSize($s)""").toSeq ++
          restrictions.maxProperties.map(s => s"""Validator.maxSize($s)""").toSeq
      validations match {
        case Nil      => ""
        case h +: Nil => s".validate(${allowNull("Iterable[?]", required)(h)})"
        case seq      => s""".validate(${allowNull("Iterable[?]", required)(s"""Validator.all(${seq.mkString(", ")})""")})"""
      }
    case _ => ""
  }
}
