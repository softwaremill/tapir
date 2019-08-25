package tapir.tests

case class FruitAmount(fruit: String, amount: Int)

case class IntWrapper(v: Int) extends AnyVal

case class ValidFruitAmount(fruit: String, amount: IntWrapper)
