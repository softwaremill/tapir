package tapir

package object tests {

  val singleQueryParam: Endpoint[String, Unit, String] = endpoint.in(query[String]("param1")).out(textBody[String])

  val twoQueryParams: Endpoint[(String, Option[Int]), Unit, String] =
    endpoint.in(query[String]("param1")).in(query[Option[Int]]("param2")).out(textBody[String])

  val singleHeader: Endpoint[String, Unit, String] = endpoint.in(header[String]("test-header")).out(textBody[String])

  val twoPathParams: Endpoint[(String, Int), Unit, String] = endpoint.in("api" / path[String] / "user" / path[Int]).out(textBody[String])

  val singleBody: Endpoint[String, Unit, String] = endpoint.post.in("echo" / "body").in(textBody[String]).out(textBody[String])

  val singleMappedValue: Endpoint[List[Char], Unit, String] =
    endpoint.in(query[String]("param1").map(_.toList)(_.mkString(""))).out(textBody[String])

  val twoMappedValues: Endpoint[StringInt, Unit, String] =
    endpoint.in(("api" / path[String] / "user" / path[Int]).map(StringInt.tupled)(si => (si.s, si.i))).out(textBody[String])

  val twoMappedValuesAndUnmapped: Endpoint[(StringInt, String), Unit, String] = endpoint
    .in(("api" / path[String] / "user" / path[Int]).map(StringInt.tupled)(si => (si.s, si.i)))
    .in(query[String]("param1"))
    .out(textBody[String])

  val singleOutMappedValue: Endpoint[String, Unit, List[Char]] =
    endpoint.in(query[String]("param1")).out(textBody[String].map(_.toList)(_.mkString("")))

  val twoOutMappedValues: Endpoint[String, Unit, StringInt] = endpoint
    .in(query[String]("param1"))
    .out(textBody[String].and(header[Int]("test-header")).map(StringInt.tupled)(StringInt.unapply(_).get))
}
