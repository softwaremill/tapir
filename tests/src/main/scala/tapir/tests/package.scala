package tapir

package object tests {

  val in_query_out_text: Endpoint[String, Unit, String] = endpoint.in(query[String]("fruit")).out(textBody[String])

  val in_query_query_out_text: Endpoint[(String, Option[Int]), Unit, String] =
    endpoint.in(query[String]("fruit")).in(query[Option[Int]]("amount")).out(textBody[String])

  val in_header_out_text: Endpoint[String, Unit, String] = endpoint.in(header[String]("X-Role")).out(textBody[String])

  val in_path_path_out_text: Endpoint[(String, Int), Unit, String] =
    endpoint.in("fruit" / path[String] / "amount" / path[Int]).out(textBody[String])

  val in_text_out_text: Endpoint[String, Unit, String] = endpoint.post.in("fruit" / "info").in(textBody[String]).out(textBody[String])

  val in_mapped_query_out_text: Endpoint[List[Char], Unit, String] =
    endpoint.in(query[String]("fruit").map(_.toList)(_.mkString(""))).out(textBody[String])

  val in_mapped_path_path_out_text: Endpoint[FruitAmount, Unit, String] =
    endpoint.in(("fruit" / path[String] / "amount" / path[Int]).map(FruitAmount.tupled)(fa => (fa.fruit, fa.amount))).out(textBody[String])

  val in_query_mapped_path_path_out_text: Endpoint[(FruitAmount, String), Unit, String] = endpoint
    .in(("fruit" / path[String] / "amount" / path[Int]).map(FruitAmount.tupled)(fa => (fa.fruit, fa.amount)))
    .in(query[String]("color"))
    .out(textBody[String])

  val in_query_out_mapped_text: Endpoint[String, Unit, List[Char]] =
    endpoint.in(query[String]("fruit")).out(textBody[String].map(_.toList)(_.mkString("")))

  val in_query_out_mapped_text_header: Endpoint[String, Unit, FruitAmount] = endpoint
    .in(query[String]("fruit"))
    .out(textBody[String].and(header[Int]("X-Role")).map(FruitAmount.tupled)(FruitAmount.unapply(_).get))
}
