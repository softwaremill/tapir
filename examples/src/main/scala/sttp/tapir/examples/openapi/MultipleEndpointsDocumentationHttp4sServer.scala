// {cat=OpenAPI documentation; effects=cats-effect; server=http4s; docs=Swagger UI; json=circe}: Documenting multiple endpoints

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-json-circe:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-swagger-ui-bundle:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-http4s-server:1.11.8
//> using dep org.http4s::http4s-blaze-server:0.23.16

package sttp.tapir.examples.openapi

import cats.effect.*
import cats.syntax.all.*
import io.circe.generic.auto.*
import org.http4s.HttpRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.ExecutionContext

object MultipleEndpointsDocumentationHttp4sServer extends IOApp:
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

  // generating and exposing the documentation in yml
  val swaggerUIRoutes: HttpRoutes[IO] =
    Http4sServerInterpreter[IO]().toRoutes(
      SwaggerInterpreter().fromEndpoints[IO](List(booksListing, addBook), "The tapir library", "1.0.0")
    )

  val routes: HttpRoutes[IO] = booksListingRoutes <+> addBookRoutes <+> swaggerUIRoutes

  override def run(args: List[String]): IO[ExitCode] =
    // starting the server
    BlazeServerBuilder[IO]
      .withExecutionContext(ec)
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
