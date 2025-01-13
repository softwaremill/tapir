// {cat=Optional value; effects=Future; server=Pekko HTTP; JSON=circe; docs=Swagger UI}: Optional returned from the server logic, resulting in 404 if None

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.11
//> using dep com.softwaremill.sttp.tapir::tapir-pekko-http-server:1.11.11
//> using dep com.softwaremill.sttp.tapir::tapir-json-circe:1.11.11
//> using dep com.softwaremill.sttp.tapir::tapir-swagger-ui-bundle:1.11.11
//> using dep ch.qos.logback:logback-classic:1.5.6

package sttp.tapir.examples.optional_content

import sttp.model.StatusCode
import sttp.tapir.generic.auto.*

@main def optionalValueExample(): Unit =
  import org.slf4j.{Logger, LoggerFactory}
  val logger: Logger = LoggerFactory.getLogger(this.getClass().getName)

  type Limit = Option[Int]

  case class Country(name: String)
  case class Author(name: String, country: Country)
  case class Genre(name: String, description: String)
  case class Book(title: String, genre: Genre, year: Int, author: Author)

  /** Descriptions of endpoints used in the example. */
  object Endpoints:
    import io.circe.generic.auto.*
    import sttp.tapir.*
    import sttp.tapir.json.circe.*

    // All endpoints report errors as strings, and have the common path prefix '/books'
    private val baseEndpoint = endpoint.errorOut(stringBody).in("books")

    // Re-usable parameter description
    private val limitParameter = query[Option[Int]]("limit").description("Maximum number of books to retrieve")

    val booksListing: PublicEndpoint[Unit, String, Vector[Book], Any] = baseEndpoint.get
      .in("list" / "all")
      .out(jsonBody[Vector[Book]])

    // Optional value from serverLogic, responding with 404 when None
    val singleBook = baseEndpoint.get
      .in("book" / query[String]("title"))
      .out(oneOf(
        oneOfVariantExactMatcher(StatusCode.NotFound, jsonBody[Option[Book]])(None),
        oneOfVariantValueMatcher(StatusCode.Ok, jsonBody[Option[Book]]) {
          case Some(book) => true
        }
      ))
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
  end Library

  //

  import Endpoints.*
  import sttp.tapir.server.ServerEndpoint
  import scala.concurrent.Future

  def booksServerEndpoints: List[ServerEndpoint[Any, Future]] =
    import scala.concurrent.ExecutionContext.Implicits.global

    def bookListingLogic(): Future[Either[String, Vector[Book]]] =
      Future {
        Right[String, Vector[Book]](Library.Books.get())
      }

    def singleBookLogic(title: String): Future[Either[String, Option[Book]]] =
      Future {
        Right(Library.Books.get().find(_.title == title))
      }

    // interpreting the endpoint description and converting it to an pekko-http route, providing the logic which
    // should be run when the endpoint is invoked.
    List(
      booksListing.serverLogic(_ => bookListingLogic()),
      singleBook.serverLogic(singleBookLogic)
    )
  end booksServerEndpoints

  def swaggerUIServerEndpoints: List[ServerEndpoint[Any, Future]] =
    import sttp.tapir.swagger.bundle.SwaggerInterpreter

    // interpreting the endpoint descriptions as yaml openapi documentation
    // exposing the docs using SwaggerUI endpoints, interpreted as an pekko-http route
    SwaggerInterpreter().fromEndpoints(List(booksListing, singleBook), "The Tapir Library", "1.0")
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

  logger.info("Welcome to the Tapir Library example!")

  logger.info("Starting the server ...")
  startServer(booksServerEndpoints ++ swaggerUIServerEndpoints)

  logger.info("Try out the API by opening the Swagger UI: http://localhost:8080/docs")
