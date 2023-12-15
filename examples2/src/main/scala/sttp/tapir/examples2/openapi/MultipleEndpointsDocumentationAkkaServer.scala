package sttp.tapir.examples2.openapi

import java.util.concurrent.atomic.AtomicReference
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import io.circe.generic.auto._
import sttp.tapir.generic.auto._
import sttp.tapir._
import sttp.tapir.json.circe._
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object MultipleEndpointsDocumentationAkkaServer extends App {
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

  val booksListingRoute = AkkaHttpServerInterpreter().toRoute(booksListing.serverLogicSuccess(_ => Future.successful(books.get())))
  val addBookRoute =
    AkkaHttpServerInterpreter().toRoute(
      addBook.serverLogicSuccess(book => Future.successful { books.getAndUpdate(books => books :+ book); () })
    )

  // generating and exposing the documentation in yml
  val swaggerUIRoute =
    AkkaHttpServerInterpreter().toRoute(
      SwaggerInterpreter().fromEndpoints[Future](List(booksListing, addBook), "The tapir library", "1.0.0")
    )

  // starting the server
  val routes = {
    import akka.http.scaladsl.server.Directives._
    concat(booksListingRoute, addBookRoute, swaggerUIRoute)
  }

  val bindAndCheck = Http().newServerAt("localhost", 8080).bindFlow(routes).map { _ =>
    // testing
    println("Go to: http://localhost:8080/docs")
    println("Press any key to exit ...")
    scala.io.StdIn.readLine()
  }

  // cleanup
  Await.result(bindAndCheck.transformWith { r => actorSystem.terminate().transform(_ => r) }, Duration.Inf)
}
