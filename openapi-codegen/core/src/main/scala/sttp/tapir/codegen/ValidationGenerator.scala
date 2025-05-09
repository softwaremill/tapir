package sttp.tapir.codegen

import io.circe.JsonNumber
import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiDocument
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{
  OpenapiSchemaArray,
  OpenapiSchemaMap,
  OpenapiSchemaNumericType,
  OpenapiSchemaObject,
  OpenapiSchemaRef,
  OpenapiSchemaString
}
import sttp.tapir.codegen.util.{DocUtils, JavaEscape}

import scala.annotation.tailrec

case class ValidationDefn(name: String, tpe: String, construct: Set[String] => Option[String], refOnly: Boolean = false)
case class ValidationDefns(defns: Map[String, ValidationDefn]) {
  def render: String = defns.values
    .flatMap { d => d.construct(defns.keySet).map(impl => s"lazy val ${d.name}Validator: Validator[${d.tpe}] = $impl") }
    .mkString("\n")
}
object ValidationDefns {
  val empty: ValidationDefns = ValidationDefns(Map.empty)
  @tailrec
  private[ValidationDefns] def filtered(defns: ValidationDefns): ValidationDefns = {
    val next = ValidationDefns(defns.defns.filter(_._2.construct(defns.defns.keySet).isDefined))
    if (next.defns.keySet == defns.defns.keySet) defns
    else filtered(next)
  }
}

object ValidationGenerator {
  private def render(n: JsonNumber): String = n.toLong.map(_.toString).getOrElse(n.toDouble.toString)
  private def allowNull(tpe: String, required: Boolean)(s: String): String = if (required) s
  else
    s"""Validator.custom[Option[$tpe]](ot => ot.map($s(_)).map{
       |  case Nil => ValidationResult.Valid
       |  case l   => ValidationResult.Invalid(l.flatMap(_.customMessage).toList)
       |}.getOrElse(ValidationResult.Valid))""".stripMargin
  private def opt(t: String, nullable: Boolean) = if (nullable) s"Option[$t]" else t
  private def singleton(name: String, tpe: String, validationDefn: String): Seq[ValidationDefn] =
    Seq(ValidationDefn(name, tpe, _ => Some(validationDefn)))

  def mkValidators(doc: OpenapiDocument): ValidationDefns = {
    val allSchemas = doc.components.map(_.schemas).getOrElse(Map.empty)

    // All schemas that have explicit validation _or_ refer to a ref, which _may_ have.
    // We need to filter this, because we may have trivial
    val unfiltered = {
      val mapped = allSchemas.flatMap { case (k, v) => genValidationDefn(allSchemas, ignoreRefs = false)(k, v).filterNot(_.refOnly) }
      if (mapped.isEmpty) ValidationDefns.empty
      else ValidationDefns(mapped.map(e => e.name -> e).toMap)
    }
    // all schemas that have explicit validation
    val noRefs = {
      val mapped = allSchemas.flatMap { case (k, v) => genValidationDefn(allSchemas, ignoreRefs = true)(k, v).filterNot(_.refOnly) }
      if (mapped.isEmpty) ValidationDefns.empty
      else ValidationDefns(mapped.map(e => e.name -> e).toMap)
    }
    assert(noRefs.defns.keySet.subsetOf(unfiltered.defns.keySet))

    // This fn produces a new validations def by taking `unfiltered`, and removing any elems containing only refs not in the `defns` set.
    // This is either a superset of or equal to `defns`, assuming that `defns` is a subset of `unfiltered`.
    // We terminate when there are no more keys to add.
    @tailrec def filtered(defns: ValidationDefns): ValidationDefns = {
      val next = ValidationDefns(unfiltered.defns.filter(_._2.construct(defns.defns.keySet).isDefined))
      if (next.defns.keySet == defns.defns.keySet) defns
      else filtered(next)
    }

    filtered(noRefs)
  }

  // the 'ignore refs'
  private def validationExists(defns: Set[String])(schema: OpenapiSchemaType, ignoreRefs: Boolean = false): Boolean =
    schema match {
      case ref: OpenapiSchemaRef             => !ignoreRefs && defns.contains(ref.stripped)
      case OpenapiSchemaArray(t, _, _, r)    => r.hasRestriction || validationExists(defns)(t, ignoreRefs)
      case OpenapiSchemaMap(t, _, r)         => r.hasRestriction || validationExists(defns)(t, ignoreRefs)
      case OpenapiSchemaObject(t, _, _, _)   => t.exists { case (_, f) => validationExists(defns)(f.`type`, ignoreRefs) }
      case OpenapiSchemaString(_, p, mi, ma) => p.isDefined || mi.isDefined || ma.isDefined
      case _                                 => false
    }
  private def genValidationDefn(
      schemas: Map[String, OpenapiSchemaType],
      ignoreRefs: Boolean
  )(name: String, schema: OpenapiSchemaType): Seq[ValidationDefn] =
    schema match {
      case r: OpenapiSchemaRef =>
        Seq(
          ValidationDefn(
            name,
            r.stripped.capitalize,
            (defns: Set[String]) => if (defns.contains(r.stripped)) Some(s"${r.stripped}Validator") else None,
            refOnly = true
          )
        )
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
        val elemValidators = genValidationDefn(schemas, ignoreRefs)(s"${name}Item", t)
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
        val maybeItemValidation: Option[Set[String] => Option[String]] = elemValidators match {
          case Nil                                                 => None
          case _ if t.isInstanceOf[OpenapiSchemaRef] && ignoreRefs => None
          case h +: _ =>
            Some(defns => if (validationExists(defns)(t, ignoreRefs)) Some(mkItemValidation(h.name + "Validator")) else None)
        }
        ((validations, maybeItemValidation) match {
          case (Nil, None) => Nil
          case (Nil, Some(maybeDefn)) =>
            Seq(ValidationDefn(name, tpeName, maybeDefn(_).map(v => allowNull(rawTpeName, !nb)(v))))
          case ((h: String) +: Nil, None) => singleton(name, tpeName, allowNull(rawTpeName, !nb)(h))
          case ((h: String) +: Nil, Some(maybeItem)) =>
            Seq(
              ValidationDefn(
                name,
                tpeName,
                defn =>
                  maybeItem(defn) match {
                    case Some(v) => Some(allowNull(rawTpeName, !nb)(s"""Validator.all(${(Seq(h, v)).mkString(", ")})"""))
                    case None    => Some(allowNull(rawTpeName, !nb)(h))
                  }
              )
            )
          case (seq, maybeItem) =>
            Seq(
              ValidationDefn(
                name,
                tpeName,
                defn => Some(allowNull(rawTpeName, !nb)(s"""Validator.all(${(seq ++ maybeItem.flatMap(_(defn))).mkString(", ")})"""))
              )
            )
        }) ++ elemValidators.filterNot(_.refOnly)
      case OpenapiSchemaMap(t, nb, restrictions) =>
        val rawTpeName = name.capitalize
        val tpeName = opt(rawTpeName, nb)
        val elemValidators = genValidationDefn(schemas, ignoreRefs)(s"${name}Item", t)
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
        val maybeItemValidation: Option[Set[String] => Option[String]] = elemValidators match {
          case Nil                                                 => None
          case _ if t.isInstanceOf[OpenapiSchemaRef] && ignoreRefs => None
          case h +: _ =>
            Some(defns => if (validationExists(defns)(t, ignoreRefs)) Some(mkElemValidation(h.name + "Validator")) else None)
        }
        ((validations, maybeItemValidation) match {
          case (Nil, None)                => Nil
          case ((h: String) +: Nil, None) => singleton(name, tpeName, allowNull(rawTpeName, !nb)(h))
          case (Nil, Some(maybeDefn)) =>
            Seq(ValidationDefn(name, tpeName, maybeDefn(_).map(v => allowNull(rawTpeName, !nb)(v))))
          case ((h: String) +: Nil, Some(maybeItem)) =>
            Seq(
              ValidationDefn(
                name,
                tpeName,
                defn =>
                  maybeItem(defn) match {
                    case Some(v) => Some(allowNull(rawTpeName, !nb)(s"""Validator.all(${(Seq(h, v)).mkString(", ")})"""))
                    case None    => Some(allowNull(rawTpeName, !nb)(h))
                  }
              )
            )
          case (seq, maybeItem) =>
            Seq(
              ValidationDefn(
                name,
                tpeName,
                defn => Some(allowNull(rawTpeName, !nb)(s"""Validator.all(${(seq ++ maybeItem.flatMap(_(defn))).mkString(", ")})"""))
              )
            )
        }) ++ elemValidators.filterNot(_.refOnly)
      case OpenapiSchemaObject(ts, rs, nb, _) =>
        val rawTpeName = name.capitalize
        val tpeName = opt(rawTpeName, nb)
        val elemValidators = ts.map { case (fn, f) =>
          genValidationDefn(schemas, ignoreRefs)(s"${name}${fn.capitalize}", f.`type`)
            .filterNot { case x => ignoreRefs && x.refOnly }
            .map(defn =>
              (
                fn,
                (
                  defn,
                  f.`type`.nullable || !rs.contains(fn),
                  f.`type`
                )
              )
            )
        }
        val validatedElemRefs: Map[String, (String, Boolean, OpenapiSchemaType)] = elemValidators
          .flatMap(_.map { case (fn, (defn, nb, tpe)) => (fn, (defn.name, nb, tpe)) }.headOption)
          .toMap
        def mkElemValidation(fname: String, validatorName: String, nullable: Boolean) = {
          val applied = if (nullable) s"obj.$fname.toSeq.flatMap($validatorName.apply)" else s"$validatorName.apply(obj.$fname)"
          s"""Validator.custom(
             |  (obj: $rawTpeName) => $applied match {
             |    case Nil => ValidationResult.Valid
             |    case errs =>
             |      val msgs: List[String] = "Object element validation failed for $rawTpeName.$fname" +:
             |        errs.flatMap(_.customMessage).toList
             |      ValidationResult.Invalid(msgs)
             |  }
             |)""".stripMargin
        }
        // TODO: Handle top-level object validations
        val validations: Seq[String] = Nil
        val elemValidations: Seq[(String, Set[String] => Boolean)] = validatedElemRefs.map { case (fn, (vn, nb, tpe)) =>
          (
            mkElemValidation(fn, (tpe match { case r: OpenapiSchemaRef => r.stripped; case _ => vn }) + "Validator", nb),
            validationExists(_: Set[String])(tpe, ignoreRefs)
          )
        }.toSeq
        val x = ((validations, elemValidations) match {
          case (Nil, Nil)                => Nil
          case ((h: String) +: Nil, Nil) => singleton(name, tpeName, allowNull(rawTpeName, !nb)(h))
          case (Nil, (h, hasValidation) +: Nil) =>
            Seq(
              ValidationDefn(
                name,
                tpeName,
                (defns: Set[String]) => if (!hasValidation(defns)) None else Some(allowNull(rawTpeName, !nb)(h))
              )
            )
          case (seq, vs) =>
            Seq(
              ValidationDefn(
                name,
                tpeName,
                (defns: Set[String]) => {
                  val filtered = vs.filterNot { case (_, hasValidation) => !hasValidation(defns) }
                  (seq ++ filtered.map(_._1)).toSeq match {
                    case Nil                => None
                    case (h: String) +: Nil => Some(h)
                    case seq                => Some(allowNull(rawTpeName, !nb)(s"""Validator.all(${(seq).mkString(", ")})"""))
                  }
                }
              )
            )
        })
        x ++ elemValidators.flatMap(_.map(_._2._1)).filterNot(_.refOnly)
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
