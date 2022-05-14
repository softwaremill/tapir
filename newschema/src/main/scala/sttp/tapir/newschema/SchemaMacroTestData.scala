package sttp.tapir.newschema

object SchemaMacroTestData {
  case class Person(name: String, age: Int)
  case class Team(v: Map[String, Person])
}
