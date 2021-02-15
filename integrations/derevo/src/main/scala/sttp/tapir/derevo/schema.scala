package sttp.tapir.derevo

import derevo.Derivation
import sttp.tapir.Schema

import scala.reflect.macros.blackbox

object schema extends Derivation[Schema] {
  def instance[A]: Schema[A] = macro SchemaCustomDerived.schema[A]

  def apply[A](description: String): Schema[A] = macro SchemaCustomDerived.schemaDescription[A]
}

class SchemaCustomDerived(val c: blackbox.Context) {

  import c.universe._

  def schema[T: c.WeakTypeTag]: c.Tree =
    q"sttp.tapir.Schema.derived[${weakTypeOf[T]}]"

  def schemaDescription[T: c.WeakTypeTag](description: c.Expr[String]): c.Tree = q"$schema.description($description)"

}
