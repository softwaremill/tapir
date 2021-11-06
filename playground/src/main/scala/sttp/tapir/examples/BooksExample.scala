package sttp.tapir.examples

import com.typesafe.scalalogging.StrictLogging
import sttp.tapir.examples.Endpoints.Limit
import sttp.tapir.generic.auto._

case class Country(name: String)
case class Author(name: String, country: Country)
case class Genre(name: String, description: String)
case class Book(title: String, genre: Genre, year: Int, author: Author)
case class BooksQuery(genre: Option[String], limit: Limit)

/** Descriptions of endpoints used in the example.
  */
object Endpoints {
  import io.circe.generic.auto._
  import sttp.tapir._
  import sttp.tapir.json.circe._

  type Limit = Option[Int]
  type AuthToken = String

  // All endpoints report errors as strings, and have the common path prefix '/books'
  private val baseEndpoint = endpoint.errorOut(stringBody).in("books")

  // The path for this endpoint will be '/books/add', as we are using the base endpoint
  val addBook: PublicEndpoint[(Book, AuthToken), String, Unit, Any] = baseEndpoint.post
    .in("add")
    .in(
      jsonBody[Book]
        .description("The book to add")
        .example(Book("Pride and Prejudice", Genre("Novel", ""), 1813, Author("Jane Austen", Country("United Kingdom"))))
    )
    .in(header[String]("X-Auth-Token").description("The token is 'secret'"))

  // Re-usable parameter description
  private val limitParameter = query[Option[Int]]("limit").description("Maximum number of books to retrieve")

  val booksListing: PublicEndpoint[Limit, String, Vector[Book], Any] = baseEndpoint.get
    .in("list" / "all")
    .in(limitParameter)
    .out(jsonBody[Vector[Book]])

  val booksListingByGenre: PublicEndpoint[BooksQuery, String, Vector[Book], Any] = baseEndpoint.get
    .in(("list" / path[String]("genre").map(Option(_))(_.get)).and(limitParameter).mapTo[BooksQuery])
    .out(jsonBody[Vector[Book]])
}

object BooksExample extends App with StrictLogging {
  import Endpoints._
  import akka.http.scaladsl.server.Route

  def openapiYamlDocumentation: String = {
    import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
    import sttp.tapir.openapi.circe.yaml._

    // interpreting the endpoint description to generate yaml openapi documentation
    val docs = OpenAPIDocsInterpreter().toOpenAPI(List(addBook, booksListing, booksListingByGenre), "The Tapir Library", "1.0")
    docs.toYaml
  }

  def booksRoutes: Route = {
    import akka.http.scaladsl.server.Directives._
    import sttp.tapir.server.akkahttp._

    import scala.concurrent.ExecutionContext.Implicits.global
    import scala.concurrent.Future

    def bookAddLogic(book: Book, token: AuthToken): Future[Either[String, Unit]] =
      Future {
        if (token != "secret") {
          logger.warn(s"Tried to access with token: $token")
          Left("Unauthorized access!!!11")
        } else {
          logger.info(s"Adding book $book")
          Library.Books.getAndUpdate(books => books :+ book)
          Right(())
        }
      }

    def bookListingLogic(limit: Limit): Future[Either[String, Vector[Book]]] =
      Future {
        Right[String, Vector[Book]](Library.getBooks(BooksQuery(None, limit)))
      }

    def bookListingByGenreLogic(query: BooksQuery): Future[Either[String, Vector[Book]]] =
      Future {
        Right[String, Vector[Book]](Library.getBooks(query))
      }

    // interpreting the endpoint description and converting it to an akka-http route, providing the logic which
    // should be run when the endpoint is invoked.
    AkkaHttpServerInterpreter().toRoute(addBook.serverLogic((bookAddLogic _).tupled)) ~
      AkkaHttpServerInterpreter().toRoute(booksListing.serverLogic(bookListingLogic)) ~
      AkkaHttpServerInterpreter().toRoute(booksListingByGenre.serverLogic(bookListingByGenreLogic))
  }

  def startServer(route: Route, yaml: String): Unit = {
    import akka.actor.ActorSystem
    import akka.http.scaladsl.Http
    import akka.http.scaladsl.server.Directives._

    import sttp.tapir.server.akkahttp._
    import sttp.tapir.swagger.SwaggerUI

    import scala.concurrent.{Await, Future}
    import scala.concurrent.duration._

    val swaggerRoutes = AkkaHttpServerInterpreter().toRoute(SwaggerUI[Future](yaml))
    val routes = route ~ swaggerRoutes
    implicit val actorSystem: ActorSystem = ActorSystem()
    Await.result(Http().newServerAt("localhost", 8080).bindFlow(routes), 1.minute)

    logger.info("Server started")
  }

  def makeClientRequest(): Unit = {
    import sttp.client3._
    import sttp.tapir.client.sttp._

    val client = SttpClientInterpreter().toQuickClient(booksListing, Some(uri"http://localhost:8080"))

    val result: Either[String, Vector[Book]] = client(Some(3))

    logger.info("Result of listing request with limit 3: " + result)
  }

  logger.info("Welcome to the Tapir Library example!")

  logger.info("Starting the server ...")
  startServer(booksRoutes, openapiYamlDocumentation)

  logger.info("Making a request to the listing endpoint ...")
  makeClientRequest()

  logger.info("Try out the API by opening the Swagger UI: http://localhost:8080/swagger")
}

object Library {
  import java.util.concurrent.atomic.AtomicReference

  val Books = new AtomicReference(
    Vector(
      Book(
        "The Sorrows of Young Werther",
        Genre("Novel", "Novel is genre"),
        1774,
        Author("Johann Wolfgang von Goethe", Country("Germany"))
      ),
      Book("Iliad", Genre("Poetry", ""), -8000, Author("Homer", Country("Greece"))),
      Book("Nad Niemnem", Genre("Novel", ""), 1888, Author("Eliza Orzeszkowa", Country("Poland"))),
      Book("The Colour of Magic", Genre("Fantasy", ""), 1983, Author("Terry Pratchett", Country("United Kingdom"))),
      Book("The Art of Computer Programming", Genre("Non-fiction", ""), 1968, Author("Donald Knuth", Country("USA"))),
      Book("Pharaoh", Genre("Novel", ""), 1897, Author("Boleslaw Prus", Country("Poland")))
    )
  )

  def getBooks(query: BooksQuery): Vector[Book] = {
    val allBooks = Books.get()
    val limitedBooks = query.limit match {
      case None    => allBooks
      case Some(l) => allBooks.take(l)
    }
    val filteredBooks = query.genre match {
      case None    => limitedBooks
      case Some(g) => limitedBooks.filter(_.genre.name.equalsIgnoreCase(g))
    }
    filteredBooks
  }
}
