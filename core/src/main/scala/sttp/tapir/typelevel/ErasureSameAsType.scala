package sttp.tapir.typelevel

import sttp.tapir.macros.ErasureSameAsTypeMacros

/** An instance should be available in the implicit scope if the erasure of `T` is equal to `T`, that is when we can check at runtime that a
  * value is of type `T` using the [[scala.reflect.ClassTag]].
  */
trait ErasureSameAsType[T]

object ErasureSameAsType extends ErasureSameAsTypeMacros
