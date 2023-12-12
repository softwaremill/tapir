package sttp.tapir.tests.data

case class FruitAmount(fruit: String, amount: Int)

case class DoubleFruit(fruitA: String, fruitB: String)

case class IntWrapper(v: Int) extends AnyVal

case class StringWrapper(v: String) extends AnyVal

case class ValidFruitAmount(fruit: StringWrapper, amount: IntWrapper)
