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

  @description("my-string")
  @encodedExample("encoded-example")
  @default(MyString("default"))
  @format("utf8")
  @Schema.annotations.deprecated
  @encodedName("encoded-name")
  @validate(Validator.pass[MyString])
  case class MyString(value: String)
}
