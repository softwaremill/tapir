package sttp.tapir

//Moved to separate file because dotty issue - https://github.com/lampepfl/dotty/issues/12498
object SchemaApplyValidationTestData {
  final case class RecursiveName(name: String, subNames: Option[Vector[RecursiveName]])

  sealed trait Animal
  sealed trait Pet extends Animal {}
  case class Dog(name: String, friends: List[Pet]) extends Pet
  case class Cat(name: String, friends: List[Pet]) extends Pet

  case class SimpleDog(name: String)
}
