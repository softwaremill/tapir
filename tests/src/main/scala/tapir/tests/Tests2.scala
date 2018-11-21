package tapir.tests

import tapir._
import tapir.docs.openapi._
import tapir.openapi.circe.yaml._
import io.circe.generic.auto._
import CaseClassSchemaDerivation._

object Tests2 extends App {
  case class Address(street: String, number: Option[Int])
  case class User(first: String, age: Int, address: Address)

  val e = endpoint
    .get
    .in("x" / path[String]("p1") / "z" / path[Int]("p2")) // each endpoint must have a path and a method
    .in(query[String]("q1").description("A q1").and(query[Int]("q2").example(99)))
    .in(query[Option[String]]("q3"))
    .out(jsonBody[User])

  val docs = e.toOpenAPI("Example 1", "1.0")
  println(docs.toYaml)
}

/*
TODO:
 * examples in docs
 * object schemas grouped in components (object labels)
 * in/out object schemas
 * mapped input
 * human-friendly type errors
 * better type inference in idea
 * 404 test
 */
