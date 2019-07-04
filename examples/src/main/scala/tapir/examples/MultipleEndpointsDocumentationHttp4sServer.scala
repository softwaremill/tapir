package tapir.examples

import java.util.Properties
import java.util.concurrent.atomic.AtomicReference

import cats.effect._
import cats.implicits._
import io.circe.generic.auto._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Location
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.syntax.kleisli._
import org.http4s.{HttpRoutes, StaticFile, Uri}
import tapir._
import tapir.docs.openapi._
import tapir.json.circe._
import tapir.openapi.OpenAPI
import tapir.openapi.circe.yaml._
import tapir.server.http4s._

import scala.concurrent.ExecutionContext

object MultipleEndpointsDocumentationHttp4sServer extends App {
  // endpoint descriptions
  case class Author(name: String)
  case class Book(title: String, year: Int, author: Author)

  val booksListing: Endpoint[Unit, Unit, Vector[Book], Nothing] = endpoint.get
    .in("books")
    .in("list" / "all")
    .out(jsonBody[Vector[Book]])

  val addBook: Endpoint[Book, Unit, Unit, Nothing] = endpoint.post
    .in("books")
    .in("add")
    .in(
      jsonBody[Book]
        .description("The book to add")
        .example(Book("Pride and Prejudice", 1813, Author("Jane Austen")))
    )

  // server-side logic
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

  val booksListingRoutes: HttpRoutes[IO] = booksListing.toRoutes(_ => IO(books.get().asRight[Unit]))
  val addBookRoutes: HttpRoutes[IO] = addBook.toRoutes(book => IO((books.getAndUpdate(books => books :+ book): Unit).asRight[Unit]))
  val routes: HttpRoutes[IO] = booksListingRoutes <+> addBookRoutes

  // generating the documentation in yml; extension methods come from imported packages
  val openApiDocs: OpenAPI = List(booksListing, addBook).toOpenAPI("The tapir library", "1.0.0")
  val openApiYml: String = openApiDocs.toYaml

  // starting the server
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit val contextShift: ContextShift[IO] = IO.contextShift(ec)
  implicit val timer: Timer[IO] = IO.timer(ec)

  BlazeServerBuilder[IO]
    .bindHttp(8080, "localhost")
    .withHttpApp(Router("/" -> routes, "/docs" -> SwaggerHttp4s.routes[IO](openApiYml)).orNotFound)
    .resource
    .use { _ =>
      IO {
        println("Go to: http://localhost:8080/docs")
        println("Press any key to exit ...")
        scala.io.StdIn.readLine()
      }
    }
    .unsafeRunSync()
}

object SwaggerHttp4s {
  private val DocsContext = "docs"
  private val DocsYaml = "docs.yml"

  private val swaggerVersion = {
    val p = new Properties()
    val pomProperties = getClass.getResourceAsStream("/META-INF/maven/org.webjars/swagger-ui/pom.properties")
    try p.load(pomProperties)
    finally pomProperties.close()
    p.getProperty("version")
  }

  def routes[F[_]: ContextShift: Sync](yml: String): HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl._

    HttpRoutes.of[F] {
      case GET -> Root =>
        PermanentRedirect(Location(Uri.fromString(s"/$DocsContext/index.html?url=/$DocsContext/$DocsYaml").right.get))
      case GET -> Root / DocsYaml =>
        Ok(yml)
      case r =>
        StaticFile
          .fromResource(s"/META-INF/resources/webjars/swagger-ui/$swaggerVersion${r.pathInfo}", ExecutionContext.global)
          .getOrElseF(NotFound())
    }
  }
}
