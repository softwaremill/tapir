package sttp.tapir.tests

import sttp.tapir._

case class FruitAmount(fruit: String, amount: Int)

case class IntWrapper(v: Int) extends AnyVal

case class StringWrapper(v: String) extends AnyVal

case class ValidFruitAmount(fruit: StringWrapper, amount: IntWrapper)

case class ColorWrapper(color: Color)

sealed trait Entity {
  def name: String
}
case class Person(name: String, age: Int) extends Entity
case class Organization(name: String) extends Entity
