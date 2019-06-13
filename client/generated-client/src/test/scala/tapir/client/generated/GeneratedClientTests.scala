package tapir.client.generated

import io.circe.generic.auto._
import org.scalatest.{FlatSpec, Matchers}
import tapir._
import tapir.json.circe._

class GeneratedClientTests extends FlatSpec with Matchers {

  type Limit = Int
  type AuthToken = String
  case class BooksFromYear(genre: String, year: Int)
  case class Book(title: String)

  val booksListing: Endpoint[(BooksFromYear, Limit, AuthToken), String, List[Book], Nothing] = endpoint.get
    .in(("books" / path[String]("genre") / path[Int]("year")).mapTo(BooksFromYear))
    .in(query[Int]("limit").description("Maximum number of books to retrieve"))
    .in(header[String]("X-Auth-Token"))
    .errorOut(stringBody)
    .out(jsonBody[List[Book]])

  it should "generate sample call" in {
    import language.Scala
    import language.scala.Sttp
    val generatedCode = new EndpointToGenerated(Scala, "sample.tapir.generated", Sttp).generate("bookApi", booksListing)

    val expectedCode =
      """import com.softwaremill.sttp._
        |
        |object bookApi {
        |
        |}
        |
        |class bookApi(basePath: String) {
        |  import bookApi._
        |
        |  class books(basePath: String) {
        |    class genre(basePath: String) {
        |       class year(basePath: String) {
        |           def GET() =
        |             sttp.get(uri"$previousPath")
        |       }
        |       def apply(year: String) =
        |         new year(basePath + "/" + year)
        |    }
        |    def apply(genre: String) =
        |      new genre(basePath + "/" + genre)
        |  }
        |  def books() =
        |    new books(basePath + "/" + books)
        |}
        |""".stripMargin

    generatedCode shouldBe expectedCode
  }
}
