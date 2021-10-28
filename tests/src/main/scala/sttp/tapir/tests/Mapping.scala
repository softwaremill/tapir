package sttp.tapir.tests

import sttp.tapir.tests.data.{Fruit, FruitAmount}
import sttp.tapir._

object Mapping {
  val in_mapped_query_out_string: PublicEndpoint[List[Char], Unit, String, Any] =
    endpoint.in(query[String]("fruit").map(_.toList)(_.mkString(""))).out(stringBody).name("mapped query")

  val in_mapped_path_out_string: PublicEndpoint[Fruit, Unit, String, Any] =
    endpoint.in(("fruit" / path[String]).mapTo[Fruit]).out(stringBody).name("mapped path")

  val in_mapped_path_path_out_string: PublicEndpoint[FruitAmount, Unit, String, Any] =
    endpoint.in(("fruit" / path[String] / "amount" / path[Int]).mapTo[FruitAmount]).out(stringBody).name("mapped path path")

  val in_query_mapped_path_path_out_string: PublicEndpoint[(FruitAmount, String), Unit, String, Any] = endpoint
    .in(("fruit" / path[String] / "amount" / path[Int]).mapTo[FruitAmount])
    .in(query[String]("color"))
    .out(stringBody)
    .name("query and mapped path path")

  val in_query_out_mapped_string: PublicEndpoint[String, Unit, List[Char], Any] =
    endpoint.in(query[String]("fruit")).out(stringBody.map(_.toList)(_.mkString(""))).name("out mapped")

  val in_query_out_mapped_string_header: PublicEndpoint[String, Unit, FruitAmount, Any] = endpoint
    .in(query[String]("fruit"))
    .out(stringBody.and(header[Int]("X-Role")).mapTo[FruitAmount])
    .name("out mapped")

  val in_header_out_header_unit_extended: PublicEndpoint[(Unit, String), Unit, (Unit, String), Any] = {
    def addInputAndOutput[I, E, O](e: PublicEndpoint[I, E, O, Any]): PublicEndpoint[(I, String), E, (O, String), Any] = {
      e.in(header[String]("X")).out(header[String]("Y"))
    }

    addInputAndOutput(endpoint.in(header("A", "1")).out(header("B", "2")))
  }

  val in_4query_out_4header_extended: PublicEndpoint[((String, String), String, String), Unit, ((String, String), String, String), Any] = {
    def addInputAndOutput[I, E, O](e: PublicEndpoint[I, E, O, Any]): PublicEndpoint[(I, String, String), E, (O, String, String), Any] = {
      e.in(query[String]("x").and(query[String]("y"))).out(header[String]("X").and(header[String]("Y")))
    }

    addInputAndOutput(endpoint.in(query[String]("a").and(query[String]("b"))).out(header[String]("A").and(header[String]("B"))))
  }

  val in_3query_out_3header_mapped_to_tuple: PublicEndpoint[(String, String, String, String), Unit, (String, String, String, String), Any] =
    endpoint
      .in(query[String]("p1"))
      .in(query[String]("p2").map(x => (x, x))(_._1))
      .in(query[String]("p3"))
      .out(header[String]("P1"))
      .out(header[String]("P2").map(x => (x, x))(_._1))
      .out(header[String]("P3"))

  val in_2query_out_2query_mapped_to_unit: PublicEndpoint[String, Unit, String, Any] =
    endpoint
      .in(query[String]("p1").map(_ => ())(_ => "DEFAULT_PARAM"))
      .in(query[String]("p2"))
      .out(header[String]("P1").map(_ => ())(_ => "DEFAULT_HEADER"))
      .out(header[String]("P2"))
      .name("mapped to unit")
}
