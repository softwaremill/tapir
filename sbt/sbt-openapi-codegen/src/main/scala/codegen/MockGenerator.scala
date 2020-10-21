package codegen

object MockGenerator {

  val mockSource =
    """|package sttp.tapir.generated
     |
     |object TapirGeneratedEndpoints {
     |  import sttp.tapir._
     |  import sttp.tapir.json.circe._
     |  import io.circe.generic.auto._
     |
     |  type Limit = Int
     |  type AuthToken = String
     |  case class BooksFromYear(genre: String, year: Int)
     |  case class Book(title: String)
     |
     |  val booksListing: Endpoint[(BooksFromYear, Limit, AuthToken), String, List[Book], Any] =
     |    endpoint
     |      .get
     |      .in(("books" / path[String]("genre") / path[Int]("year")).mapTo(BooksFromYear))
     |      .in(query[Limit]("limit").description("Maximum number of books to retrieve"))
     |      .in(header[AuthToken]("X-Auth-Token"))
     |      .errorOut(stringBody)
     |      .out(jsonBody[List[Book]])
     |
     |  val generatedEndpoints = Seq(booksListing)
     |}
     |""".stripMargin

}
