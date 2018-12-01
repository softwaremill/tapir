package tapir

import Schema._
import language.experimental.macros
import magnolia._

sealed trait Schema

// TODO: configuration; camel-case/normal case
// TODO: test recurrence
object Schema {
  case object SString extends Schema {
    override def toString: String = "string"
  }
  case object SInt extends Schema {
    override def toString: String = "int"
  }
  case class SObject(fields: Iterable[(String, Schema)], required: Iterable[String]) extends Schema {
    override def toString: String = s"object(${fields.map(f => s"${f._1}->${f._2}").mkString(",")};required:${required.mkString(",")})"
  }
}

trait SchemaFor[T] {
  def schema: Schema
  def isOptional: Boolean = false
  override def toString: String = s"schema is $schema"
}
object SchemaFor extends SchemaForMagnoliaDerivation {
  implicit case object SchemaForString extends SchemaFor[String] {
    override val schema: Schema = SString
  }
  implicit case object SchemaForInt extends SchemaFor[Int] {
    override val schema: Schema = SInt
  }
  implicit def schemaForOption[T: SchemaFor]: SchemaFor[Option[T]] = new SchemaFor[Option[T]] {
    override def schema: Schema = implicitly[SchemaFor[T]].schema
  }
}

trait SchemaForMagnoliaDerivation {
  type Typeclass[T] = SchemaFor[T]

  def combine[T](ctx: CaseClass[SchemaFor, T]): SchemaFor[T] = {
    new SchemaFor[T] {
      override val schema: Schema = SObject(
        ctx.parameters.map(p => (p.label, p.typeclass.schema)).toList,
        ctx.parameters.filter(!_.typeclass.isOptional).map(_.label)
      )
    }
  }

  def dispatch[T](ctx: SealedTrait[SchemaFor, T]): SchemaFor[T] = {
    throw new RuntimeException("Sealed trait hierarchies are not yet supported")
  }

  implicit def schemaForCaseClass[T]: SchemaFor[T] = macro Magnolia.gen[T]
}
