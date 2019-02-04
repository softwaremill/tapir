package tapir.tests
import tapir.openapi.Info

object Tests4 {
  import tapir._
  import tapir.json.circe._
  import io.circe.generic.auto._

  type Limit = Int
  type AuthToken = String
  case class BooksFromYear(genre: String, year: Int)
  case class Book(title: String)

  val booksListing: Endpoint[(BooksFromYear, Limit, AuthToken), String, List[Book], Nothing] = endpoint.get
    .in(("books" / path[String]("genre") / path[Int]("year")).mapTo(BooksFromYear))
    .in(query[Int]("limit").description("Maximum number of products to retrieve"))
    .in(header[String]("X-Auth-Token"))
    .errorOut(stringBody)
    .out(jsonBody[List[Book]])

  //

  {
    import tapir.docs.openapi._
    import tapir.openapi.circe._
    import tapir.openapi.circe.yaml._

    val docs = booksListing.toOpenAPI(Info("My Bookshop", "1.0"))
    println(docs.toYaml)
  }

  //
  {
    import tapir.server.akkahttp._
    import akka.http.scaladsl.server.Route
    import scala.concurrent.Future

    def bookListingLogic(bfy: BooksFromYear, limit: Limit, at: AuthToken): Future[Either[String, List[Book]]] =
      Future.successful(Right(List(Book("The Sorrows of Young Werther"))))
    val booksListingRoute: Route = booksListing.toRoute(bookListingLogic _)
  }
  //

  {
    import tapir.client.sttp._
    import com.softwaremill.sttp._

    val booksListingRequest: Request[Either[String, List[Book]], Nothing] = booksListing
      .toSttpRequest(uri"http://localhost:8080")
      .apply(BooksFromYear("SF", 2016), 20, "xyz-abc-123")
  }
}
