package tapir

import io.circe.generic.auto._

package object tests {

  val in_query_out_string: Endpoint[String, Unit, String] = endpoint.in(query[String]("fruit")).out(stringBody)

  val in_query_query_out_string: Endpoint[(String, Option[Int]), Unit, String] =
    endpoint.in(query[String]("fruit")).in(query[Option[Int]]("amount")).out(stringBody)

  val in_header_out_string: Endpoint[String, Unit, String] = endpoint.in(header[String]("X-Role")).out(stringBody)

  val in_path_path_out_string: Endpoint[(String, Int), Unit, String] =
    endpoint.in("fruit" / path[String] / "amount" / path[Int]).out(stringBody)

  val in_string_out_string: Endpoint[String, Unit, String] = endpoint.post.in("fruit" / "info").in(stringBody).out(stringBody)

  val in_mapped_query_out_string: Endpoint[List[Char], Unit, String] =
    endpoint.in(query[String]("fruit").map(_.toList)(_.mkString(""))).out(stringBody)

  val in_mapped_path_out_string: Endpoint[Fruit, Unit, String] =
    endpoint.in(("fruit" / path[String]).mapTo(Fruit)).out(stringBody)

  val in_mapped_path_path_out_string: Endpoint[FruitAmount, Unit, String] =
    endpoint.in(("fruit" / path[String] / "amount" / path[Int]).mapTo(FruitAmount)).out(stringBody)

  val in_query_mapped_path_path_out_string: Endpoint[(FruitAmount, String), Unit, String] = endpoint
    .in(("fruit" / path[String] / "amount" / path[Int]).mapTo(FruitAmount))
    .in(query[String]("color"))
    .out(stringBody)

  val in_query_out_mapped_string: Endpoint[String, Unit, List[Char]] =
    endpoint.in(query[String]("fruit")).out(stringBody.map(_.toList)(_.mkString("")))

  val in_query_out_mapped_string_header: Endpoint[String, Unit, FruitAmount] = endpoint
    .in(query[String]("fruit"))
    .out(stringBody.and(header[Int]("X-Role")).mapTo(FruitAmount))

  val in_json_out_string: Endpoint[FruitAmount, Unit, String] =
    endpoint.post.in("fruit" / "info").in(jsonBody[FruitAmount]).out(stringBody)

  val in_string_out_json: Endpoint[String, Unit, FruitAmount] =
    endpoint.post.in("fruit" / "info").in(stringBody).out(jsonBody[FruitAmount])
}
