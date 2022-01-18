package sttp.tapir

object SchemaMacroTestData {
  case class ArrayWrapper(f1: List[String])
  case class Person(name: String, age: Int)
  case class DevTeam(p1: Person, p2: Person)
  case class Parent(child: Option[Person])
  case class Team(v: Map[String, Person])
  case class DoubleValue(value: Double) extends AnyVal

  case class Wrapper(e: Entity, s: String)
  case class WrapperT[A, B, C](e: Entity, a: A, b: B, c: C)

  sealed trait Entity {
    def kind: String
    def kind(s: String): String
  }
  case class User(firstName: String, lastName: String) extends Entity {
    def kind: String = "user"
    def kind(s: String): String = s match {
      case "user" => "user"
      case _      => throw new MatchError(s)
    }
  }
  case class Organization(name: String) extends Entity {
    def kind: String = "org"
    def kind(s: String): String = s match {
      case "org" => "org"
      case _     => throw new MatchError(s)
    }
  }
}
