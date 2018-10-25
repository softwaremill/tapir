package sapi.tests

import sapi._
import sapi.docs.openapi._
import sapi.openapi.circe.yaml._

object Tests2 extends App {
  val e = endpoint
    .get()
    .in("x" / pathCapture[String]("p1") / "z" / pathCapture[Int]("p2")) // each endpoint must have a path and a method
    .in(query[String]("q1").description("A q1").and(query[Int]("q2").example(99)))
    .in(query[Option[String]]("q3"))
    .out[String]

  val docs = e.toOpenAPI("Example 1", "1.0")
  println(docs.toYaml)
}
