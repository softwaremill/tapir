package sttp.tapir.testdata

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

  case object implicitly extends BuiltinTypenameCollisionEnum
  case object identity extends BuiltinTypenameCollisionEnum

  val schema: sttp.tapir.Schema[BuiltinTypenameCollisionEnum] =
    sttp.tapir.Schema.derivedEnumeration[BuiltinTypenameCollisionEnum].defaultStringBased
}
