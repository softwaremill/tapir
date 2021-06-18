package sttp.tapir.tests

sealed trait Entity {
  def name: String
}
case class Person(name: String, age: Int) extends Entity
case class Organization(name: String) extends Entity
