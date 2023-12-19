package sttp.tapir.namespacing

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.Schema

class SchemaMacroNamespaceTest extends AnyFlatSpec with Matchers {

  // #2254: the macro should reference fully qualified class names
  it should "compile macro-generated code using derivedEnumeration in a package other than sttp.tapir" in {
    sealed trait MyProduct
    object MyProduct {
      def fromString(m: String): Option[MyProduct] = ???
    }
    case object V1 extends MyProduct

    import sttp.tapir.Codec
    Codec.derivedEnumeration[String, MyProduct](MyProduct.fromString, _.toString)
  }

  it should "compile macro-generated code for shadowed _root_.scala names" in {

    /** @see [[https://github.com/softwaremill/tapir/issues/3407 Github Issue #3407]] */
    sealed abstract class BuiltinTypenameCollisionEnum

    object BuiltinTypenameCollisionEnum {
      case object Option extends BuiltinTypenameCollisionEnum
      case object Some extends BuiltinTypenameCollisionEnum
      case object None extends BuiltinTypenameCollisionEnum
      case object List extends BuiltinTypenameCollisionEnum
      case object Nil extends BuiltinTypenameCollisionEnum
      case object Map extends BuiltinTypenameCollisionEnum
      case object Array extends BuiltinTypenameCollisionEnum
      case object Either extends BuiltinTypenameCollisionEnum
      case object Left extends BuiltinTypenameCollisionEnum
      case object Right extends BuiltinTypenameCollisionEnum
      case object Unit extends BuiltinTypenameCollisionEnum

      case object implicitly extends BuiltinTypenameCollisionCaseClass
      case object identity extends BuiltinTypenameCollisionCaseClass

      val schema: sttp.tapir.Schema[BuiltinTypenameCollisionEnum] =
        sttp.tapir.Schema.derivedEnumeration[BuiltinTypenameCollisionEnum].defaultStringBased
    }

    sealed abstract class BuiltinTypenameCollisionCaseClass

    object BuiltinTypenameCollisionCaseClass {

      import sttp.tapir.generic.auto._

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

      val schema: sttp.tapir.Schema[BuiltinTypenameCollisionCaseClass] =
        sttp.tapir.Schema.oneOfWrapped[BuiltinTypenameCollisionCaseClass]
    }

    assert(
      List(BuiltinTypenameCollisionCaseClass.schema, BuiltinTypenameCollisionEnum.schema).flatMap(_.name).nonEmpty
    )
  }

}
