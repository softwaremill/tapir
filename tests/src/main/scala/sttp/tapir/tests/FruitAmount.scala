package sttp.tapir.tests

import sttp.tapir._

import scala.collection.immutable

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

object Organization {
  def fromXml(xml: String): Organization = {
    // <name>xxx</name>
    Organization(xml.split(">")(1).split("<").head)
  }
}

object Enumeratum {
  import enumeratum.{Enum, EnumEntry}

  case class FruitWithEnum(fruit: String, amount: Int, fruitType: List[FruitType])

  sealed trait FruitType extends EnumEntry

  object FruitType extends Enum[FruitType] {
    case object APPLE extends FruitType
    case object PEAR extends FruitType
    override def values: immutable.IndexedSeq[FruitType] = findValues
  }
}
