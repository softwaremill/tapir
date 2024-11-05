// {cat=OpenAPI documentation; effects=Future; server=Pekko HTTP; docs=Swagger UI; json=circe}: Documenting multiple endpoints

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-json-circe:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-swagger-ui-bundle:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-pekko-http-server:1.11.8

package sttp.tapir.examples.openapi

import java.util.concurrent.atomic.AtomicReference
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import io.circe.generic.auto.*
import sttp.tapir.generic.auto.*
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.pekkohttp.PekkoHttpServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter

import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}

@main def multipleEndpointsDocumentationPekkoServer(): Unit =
  implicit val actorSystem: ActorSystem = ActorSystem()
  import actorSystem.dispatcher

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

  val booksListingRoute = PekkoHttpServerInterpreter().toRoute(booksListing.serverLogicSuccess(_ => Future.successful(books.get())))
  val addBookRoute =
    PekkoHttpServerInterpreter().toRoute(
      addBook.serverLogicSuccess(book => Future.successful { books.getAndUpdate(books => books :+ book); () })
    )

  // generating and exposing the documentation in yml
  val swaggerUIRoute =
    PekkoHttpServerInterpreter().toRoute(
      SwaggerInterpreter().fromEndpoints[Future](List(booksListing, addBook), "The tapir library", "1.0.0")
    )

  // starting the server
  val routes = {
    import org.apache.pekko.http.scaladsl.server.Directives.*
    concat(booksListingRoute, addBookRoute, swaggerUIRoute)
  }

  val bindAndCheck = Http().newServerAt("localhost", 8080).bindFlow(routes).map { binding =>
    // testing
    println("Go to: http://localhost:8080/docs")
    println("Press any key to exit ...")
    scala.io.StdIn.readLine()

    binding
  }

  // cleanup
  val _ = Await.result(bindAndCheck.flatMap(_.terminate(1.minute)), 1.minute)
