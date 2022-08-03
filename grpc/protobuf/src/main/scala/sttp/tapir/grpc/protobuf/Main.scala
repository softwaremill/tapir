package sttp.tapir.grpc.protobuf

import sttp.tapir.generic.auto._
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter

// type Limit = Option[Int]
// type AuthToken = String

case class Country(name: String)
case class Author(name: String, country: Country)
case class Genre(name: String, description: String)
case class Book(title: String, genre: Genre, year: Int, author: Author)
case class SimpleBook(title: String)
case class BooksQuery(genre: Option[String], limit: Option[Int])

/** Descriptions of endpoints used in the example.
  */
object Endpoints {
  // import io.circe.generic.auto._
  import sttp.tapir._

  // All endpoints report errors as strings, and have the common path prefix '/books'
  private val baseEndpoint = endpoint.errorOut(stringBody).in("books")

  // The path for this endpoint will be '/books/add', as we are using the base endpoint
  // val addBook: PublicEndpoint[(Book, AuthToken), String, Unit, Any] = baseEndpoint.post
  //   .in("add")
  //   .in(
  //     grpcBody[Book]
  //       .description("The book to add")
  //       .example(Book("Pride and Prejudice", Genre("Novel", ""), 1813, Author("Jane Austen", Country("United Kingdom"))))
  //   )
  //   .in(header[AuthToken]("X-Auth-Token").description("The token is 'secret'"))

  val addBook = endpoint.in(grpcBody[SimpleBook])

  // // Re-usable parameter description
  // private val limitParameter = query[Option[Int]]("limit").description("Maximum number of books to retrieve")

  // val booksListing: PublicEndpoint[Limit, String, Vector[Book], Any] = baseEndpoint.get
  //   .in("list" / "all")
  //   .in(limitParameter)
  //   .out(jsonBody[Vector[Book]])

  // val booksListingByGenre: PublicEndpoint[BooksQuery, String, Vector[Book], Any] = baseEndpoint.get
  //   .in(("list" / path[String]("genre").map(Option(_))(_.get)).and(limitParameter).mapTo[BooksQuery])
  //   .out(jsonBody[Vector[Book]])

  val proto = new ProtobufInterpreter(new EndpointToProtobufMessage()).toProtobuf(List(addBook))

  println(proto)
  println()
  println()

  // new ProtoSchemaRegistry(new ProtoRenderer(), "grpc/protobuf/src/protobuf/main.proto").register(proto)
}

class BooksExample {

  //

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

  //

  import Endpoints._
  import sttp.tapir.server.ServerEndpoint
  import scala.concurrent.Future

  def booksServerEndpoints: List[ServerEndpoint[Any, Future]] = {
    import scala.concurrent.ExecutionContext.Implicits.global

    def bookAddLogic(book: Book, token: String): Future[Either[String, Unit]] =
      Future {
        if (token != "secret") {
          //   logger.warn(s"Tried to access with token: $token")
          Left("Unauthorized access!!!11")
        } else {
          //   logger.info(s"Adding book $book")
          Library.Books.getAndUpdate(books => books :+ book)
          Right(())
        }
      }

    // def bookListingLogic(limit: Limit): Future[Either[String, Vector[Book]]] =
    //   Future {
    //     Right[String, Vector[Book]](Library.getBooks(BooksQuery(None, limit)))
    //   }

    // def bookListingByGenreLogic(query: BooksQuery): Future[Either[String, Vector[Book]]] =
    //   Future {
    //     Right[String, Vector[Book]](Library.getBooks(query))
    //   }

    // interpreting the endpoint description and converting it to an akka-http route, providing the logic which
    // should be run when the endpoint is invoked.
    List(
      // addBook.serverLogic((bookAddLogic _).tupled),
      //   booksListing.serverLogic(bookListingLogic),
      //   booksListingByGenre.serverLogic(bookListingByGenreLogic)
    )
  }

// def swaggerUIServerEndpoints: String = {
//     import sttp.apispec.openapi.circe.yaml._
//     // interpreting the endpoint descriptions as yaml openapi documentation
//     // exposing the docs using SwaggerUI endpoints, interpreted as an akka-http route
//     OpenAPIDocsInterpreter().toOpenAPI(addBook, "My Bookshop", "1.0").toYaml

//   }

//   println(swaggerUIServerEndpoints)

}

object Main extends ProtoSchemaRegistry {
  val renderer: ProtoRenderer = new ProtoRenderer()
  val path: String = "grpc/protobuf/src/main/protobuf/main.proto"
  val proto: Protobuf = Endpoints.proto

  register()
}
