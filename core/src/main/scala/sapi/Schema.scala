package sapi

import Schema._
import language.experimental.macros
import magnolia._

sealed trait Schema

// TODO: configuration; camel-case/normal case
// TODO: test recurrence
object Schema {
  case object SEmpty extends Schema {
    override def toString: String = "-"
  }
  case object SString extends Schema {
    override def toString: String = "string"
  }
  case object SInt extends Schema {
    override def toString: String = "int"
  }
  case class SObject(fields: Iterable[(String, Schema)], required: Iterable[String]) extends Schema {
    override def toString: String = s"object(${fields.map(f => s"${f._1}->${f._2}").mkString(",")},required:${required.mkString(",")})"
  }
}

trait SchemaFor[T] {
  def schema: Schema
  def isOptional: Boolean = false
  override def toString: String = s"schema is $schema"
}
object SchemaFor {
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

object CaseClassSchemaDerivation {
  type Typeclass[T] = SchemaFor[T]

  def combine[T](ctx: CaseClass[SchemaFor, T]): SchemaFor[T] = {
    println("COMBINE " + ctx)
    new SchemaFor[T] {
      override val schema: Schema = SObject(
        ctx.parameters.map(p => (p.label, p.typeclass.schema)).toList,
        ctx.parameters.filter(!_.typeclass.isOptional).map(_.label)
      )
    }
  }

  def dispatch[T](ctx: SealedTrait[SchemaFor, T]): SchemaFor[T] = {
    println("DISPATCH " + ctx)
    null
  }

  implicit def gen[T]: SchemaFor[T] = macro Magnolia.gen[T]
}

object TestX extends App {

  class First(f: String)
  case class Name(first: First, middle: Option[String], last: String)
  case class User(name: Name, age: Int)

  implicit val schemaForFirst: SchemaFor[First] = new SchemaFor[First] { override def schema: Schema = SString }

  val genUser = CaseClassSchemaDerivation.gen[User]
  println(genUser)
}
