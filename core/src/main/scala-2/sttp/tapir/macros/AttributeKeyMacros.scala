package sttp.tapir.macros

import sttp.tapir.AttributeKey
import sttp.tapir.internal.AttributeKeyMacro

trait AttributeKeyMacros {
  def apply[T]: AttributeKey[T] = macro AttributeKeyMacro[T]
}
