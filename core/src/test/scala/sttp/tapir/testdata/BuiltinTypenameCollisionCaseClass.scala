package sttp.tapir.testdata

sealed abstract class BuiltinTypenameCollisionCaseClass

object BuiltinTypenameCollisionCaseClass {

  case class Option(a: String) extends BuiltinTypenameCollisionCaseClass
  case class Some(a: Int) extends BuiltinTypenameCollisionCaseClass
  case class None(b: Boolean) extends BuiltinTypenameCollisionCaseClass
  case class List(c: String) extends BuiltinTypenameCollisionCaseClass
  case class Nil(b: String) extends BuiltinTypenameCollisionCaseClass
  case class Map(d: Int) extends BuiltinTypenameCollisionCaseClass

  /** can't make this work - it either can't derive schema, or when schema is explicit in companion like below, it fails with next
    * alphabetical co-product part. Magnolia issue?
    * {{{
    *  case class Array(a: Boolean) extends BuiltinTypenameCollisionCaseClass
    *
    *  object Array {
    *    implicit lazy val schema: sttp.tapir.Schema[Array] = Schema(
    *      sttp.tapir.SchemaType.SProduct(
    *        scala.List(
    *          sttp.tapir.SchemaType.SProductField(
    *            sttp.tapir.FieldName("a"),
    *            Schema.schemaForBoolean,
    *            (_: Any) => scala.None
    *          )
    *        )
    *      )
    *    )
    *  }
    * }}}
    */

  case class Either(e: String) extends BuiltinTypenameCollisionCaseClass
  case class Left(x: Double) extends BuiltinTypenameCollisionCaseClass
  case class Right(y: Double) extends BuiltinTypenameCollisionCaseClass
  case class Unit() extends BuiltinTypenameCollisionCaseClass
  case class implicitly(a: Int) extends BuiltinTypenameCollisionCaseClass
  case class identity(b: Boolean) extends BuiltinTypenameCollisionCaseClass

  import sttp.tapir.generic.auto._

  val schema: sttp.tapir.Schema[BuiltinTypenameCollisionCaseClass] =
    sttp.tapir.Schema.oneOfWrapped[BuiltinTypenameCollisionCaseClass]
}
