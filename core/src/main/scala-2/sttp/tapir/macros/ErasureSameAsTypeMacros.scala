package sttp.tapir.macros

import sttp.tapir.internal.ErasureSameAsTypeMacro
import sttp.tapir.typelevel.ErasureSameAsType

trait ErasureSameAsTypeMacros {
  implicit def instance[T]: ErasureSameAsType[T] = macro ErasureSameAsTypeMacro.instance[T]
}
