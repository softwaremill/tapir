// {cat=Hello, World!; effects=Future; server=Pekko HTTP; client=sttp3; JSON=circe; docs=Swagger UI}: A demo of Tapir's capabilities

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-pekko-http-server:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-json-circe:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-swagger-ui-bundle:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-sttp-client:1.11.8
//> using dep org.apache.pekko::pekko-http:1.0.1
//> using dep org.apache.pekko::pekko-stream:1.0.3
//> using dep ch.qos.logback:logback-classic:1.5.6

package sttp.tapir.examples

import sttp.tapir.generic.auto.*

@main def booksExample(): Unit =
  import org.slf4j.{Logger, LoggerFactory}
  val logger: Logger = LoggerFactory.getLogger(this.getClass().getName)

  type Limit = Option[Int]
  type AuthToken = String

  case class Country(name: String)
  case class Author(name: String, country: Country)
  case class Genre(name: String, description: String)
  case class Book(title: String, genre: Genre, year: Int, author: Author)
  case class BooksQuery(genre: Option[String], limit: Limit)

  /** Descriptions of endpoints used in the example. */
  object Endpoints:
    import io.circe.generic.auto.*
    import sttp.tapir.*
    import sttp.tapir.json.circe.*

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
      .in(header[AuthToken]("X-Auth-Token").description("The token is 'secret'"))

    // Re-usable parameter description
    private val limitParameter = query[Option[Int]]("limit").description("Maximum number of books to retrieve")

    val booksListing: PublicEndpoint[Limit, String, Vector[Book], Any] = baseEndpoint.get
      .in("list" / "all")
      .in(limitParameter)
      .out(jsonBody[Vector[Book]])

    val booksListingByGenre: PublicEndpoint[BooksQuery, String, Vector[Book], Any] = baseEndpoint.get
      .in(("list" / path[String]("genre").map(Option(_))(_.get)).and(limitParameter).mapTo[BooksQuery])
      .out(jsonBody[Vector[Book]])
  end Endpoints

  //

  object Library:
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

    def getBooks(query: BooksQuery): Vector[Book] =
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
  end Library

  //

  import Endpoints.*
  import sttp.tapir.server.ServerEndpoint
  import scala.concurrent.Future

  def booksServerEndpoints: List[ServerEndpoint[Any, Future]] =
    import scala.concurrent.ExecutionContext.Implicits.global

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

    // interpreting the endpoint description and converting it to an pekko-http route, providing the logic which
    // should be run when the endpoint is invoked.
    List(
      addBook.serverLogic(bookAddLogic.tupled),
      booksListing.serverLogic(bookListingLogic),
      booksListingByGenre.serverLogic(bookListingByGenreLogic)
    )
  end booksServerEndpoints

  def swaggerUIServerEndpoints: List[ServerEndpoint[Any, Future]] =
    import sttp.tapir.swagger.bundle.SwaggerInterpreter

    // interpreting the endpoint descriptions as yaml openapi documentation
    // exposing the docs using SwaggerUI endpoints, interpreted as an pekko-http route
    SwaggerInterpreter().fromEndpoints(List(addBook, booksListing, booksListingByGenre), "The Tapir Library", "1.0")
  end swaggerUIServerEndpoints

  def startServer(serverEndpoints: List[ServerEndpoint[Any, Future]]): Unit =
    import org.apache.pekko.actor.ActorSystem
    import org.apache.pekko.http.scaladsl.Http

    import scala.concurrent.Await
    import scala.concurrent.duration.*

    import sttp.tapir.server.pekkohttp.PekkoHttpServerInterpreter

    implicit val actorSystem: ActorSystem = ActorSystem()
    import actorSystem.dispatcher
    val routes = PekkoHttpServerInterpreter().toRoute(serverEndpoints)
    Await.result(Http().newServerAt("localhost", 8080).bindFlow(routes), 1.minute)

    logger.info("Server started")
  end startServer

  def makeClientRequest(): Unit =
    import sttp.client3.*
    import sttp.tapir.client.sttp.SttpClientInterpreter

    val client = SttpClientInterpreter().toQuickClient(booksListing, Some(uri"http://localhost:8080"))

    val result: Either[String, Vector[Book]] = client(Some(3))

    logger.info("Result of listing request with limit 3: " + result)
  end makeClientRequest

  logger.info("Welcome to the Tapir Library example!")

  logger.info("Starting the server ...")
  startServer(booksServerEndpoints ++ swaggerUIServerEndpoints)

  logger.info("Making a request to the listing endpoint ...")
  makeClientRequest()

  logger.info("Try out the API by opening the Swagger UI: http://localhost:8080/docs")
