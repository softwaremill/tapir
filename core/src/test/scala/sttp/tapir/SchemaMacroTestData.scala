package sttp.tapir

import sttp.tapir.Schema.annotations._

object SchemaMacroTestData {
  case class ArrayWrapper(f1: List[String])
  case class Person(name: String, age: Int)
  case class DevTeam(p1: Person, p2: Person)
  case class Parent(child: Option[Person])
  case class Team(v: Map[String, Person])

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

  sealed abstract class Pet {
    @description("name")
    def name: String
  }

  case class Cat(@description("cat name") name: String, @description("cat food") catFood: String) extends Pet

  case class Dog(name: String, @description("dog food") dogFood: String) extends Pet

  sealed trait Rodent extends Pet {
    @description("likes nuts?")
    def likesNuts: Boolean
  }

  case class Hamster(name: String, likesNuts: Boolean) extends Rodent

  @description("country")
  @default(Countries.PL)
  @encodedName("country-encoded-name")
  object Countries extends Enumeration {
    type Country = Value
    val PL, NL = Value
  }

  @description("it's a small alphabet")
  sealed trait Letters
  object Letters {
    case object A extends Letters
    case object B extends Letters
    case object C extends Letters
  }

  @encodedName("CustomHericium")
  sealed trait Hericium {
    @encodedName("customCommonField")
    @description("A common field")
    val commonField: Int
  }

  object Hericium {

    @encodedName("CustomErinaceus")
    final case class Erinaceus(commonField: Int, e: String) extends Hericium
    final case class Abietis(commonField: Int, a: Int) extends Hericium
    final case class Botryoides(commonField: Int, b: Boolean) extends Hericium
  }
}
