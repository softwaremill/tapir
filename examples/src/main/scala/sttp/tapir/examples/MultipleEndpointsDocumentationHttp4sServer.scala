package sttp.tapir.examples

import cats.effect._
import cats.syntax.all._
import io.circe.generic.auto._
import org.http4s.HttpRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import org.http4s.syntax.kleisli._
import sttp.tapir._
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.openapi.OpenAPI
import sttp.tapir.openapi.circe.yaml._
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.swagger.SwaggerUI

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.ExecutionContext

object MultipleEndpointsDocumentationHttp4sServer extends IOApp {
  // endpoint descriptions
  case class Author(name: String)
  case class Book(title: String, year: Int, author: Author)

  val booksListing: PublicEndpoint[Unit, Unit, Vector[Book], Any] = endpoint.get
    .in("books")
    .in("list" / "all")
    .out(jsonBody[Vector[Book]])

  val addBook: PublicEndpoint[Book, Unit, Unit, Any] = endpoint.post
    .in("books")
    .in("add")
    .in(
      jsonBody[Book]
        .description("The book to add")
        .example(Book("Pride and Prejudice", 1813, Author("Jane Austen")))
    )

  // server-side logic
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  val books = new AtomicReference(
    Vector(
      Book("The Sorrows of Young Werther", 1774, Author("Johann Wolfgang von Goethe")),
      Book("Iliad", -8000, Author("Homer")),
      Book("Nad Niemnem", 1888, Author("Eliza Orzeszkowa")),
      Book("The Colour of Magic", 1983, Author("Terry Pratchett")),
      Book("The Art of Computer Programming", 1968, Author("Donald Knuth")),
      Book("Pharaoh", 1897, Author("Boleslaw Prus"))
    )
  )

  val booksListingRoutes: HttpRoutes[IO] =
    Http4sServerInterpreter[IO]().toRoutes(booksListing.serverLogicSuccess(_ => IO(books.get())))
  val addBookRoutes: HttpRoutes[IO] =
    Http4sServerInterpreter[IO]().toRoutes(
      addBook.serverLogicSuccess(book => IO((books.getAndUpdate(books => books :+ book): Unit)))
    )

  // generating the documentation in yml; extension methods come from imported packages
  val openApiDocs: OpenAPI = OpenAPIDocsInterpreter().toOpenAPI(List(booksListing, addBook), "The tapir library", "1.0.0")
  val openApiYml: String = openApiDocs.toYaml

  val swaggerUIRoutes: HttpRoutes[IO] = Http4sServerInterpreter[IO]().toRoutes(SwaggerUI[IO](openApiYml))
  val routes: HttpRoutes[IO] = booksListingRoutes <+> addBookRoutes <+> swaggerUIRoutes

  override def run(args: List[String]): IO[ExitCode] = {
    // starting the server
    BlazeServerBuilder[IO](ec)
      .bindHttp(8080, "localhost")
      .withHttpApp(Router("/" -> (routes)).orNotFound)
      .resource
      .use { _ =>
        IO {
          println("Go to: http://localhost:8080/docs")
          println("Press any key to exit ...")
          scala.io.StdIn.readLine()
        }
      }
      .as(ExitCode.Success)
  }
}
