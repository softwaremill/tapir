package tapir.tests

import tapir.Schema.SString
import tapir.{Schema, SchemaFor}

object TestDerivation extends App {

  class First(f: String)
  case class Name(first: First, middle: Option[String], last: String)
  case class User(name: Name, age: Int)

  implicit val schemaForFirst: SchemaFor[First] = new SchemaFor[First] { override def schema: Schema = SString }

  val genUser = implicitly[SchemaFor[User]]
  println(genUser)
}
