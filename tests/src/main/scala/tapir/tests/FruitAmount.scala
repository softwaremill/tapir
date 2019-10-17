package tapir.tests

import tapir._

case class FruitAmount(fruit: String, amount: Int)

case class IntWrapper(v: Int) extends AnyVal

case class StringWrapper(v: String) extends AnyVal

case class ValidFruitAmount(fruit: StringWrapper, amount: IntWrapper)

case class ValidFruitAmountEnum(fruit: StringWrapper, amount: IntWrapper, color: Color)
