package sttp.tapir

object SchemaMacroTestData {
  case class ArrayWrapper(f1: List[String])
  case class Person(name: String, age: Int)
  case class DevTeam(p1: Person, p2: Person)
  case class Parent(child: Option[Person])
  case class Team(v: Map[String, Person])

  sealed trait Entity {
    def kind: String
  }
  case class User(firstName: String, lastName: String) extends Entity {
    def kind: String = "user"
  }
  case class Organization(name: String) extends Entity {
    def kind: String = "org"
  }
}
