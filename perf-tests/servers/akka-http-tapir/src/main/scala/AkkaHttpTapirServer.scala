import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route

import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter

import io.circe.generic.auto._

import scala.io.StdIn
import scala.concurrent.Future


final case class Book(id: Int)

object AkkaHttpTapirServer extends App {
  implicit val actorSystem = ActorSystem(Behaviors.empty, "akka-http")
  implicit val executionContext = actorSystem.executionContext

  val bookEndpoint: PublicEndpoint[(Int), String, Book, Any] = endpoint
    .get
    .in("akka-http-tapir")
    .in(path[Int]("id"))
    .errorOut(stringBody)
    .out(jsonBody[Book])

  def bookEndpointLogic(id: Int): Future[Either[String, Book]] =
    Future.successful(Right(Book(id)))

  val route: Route = AkkaHttpServerInterpreter()
    .toRoute(bookEndpoint.serverLogic(bookEndpointLogic))

  val bindingFuture = Http()
    .newServerAt("127.0.0.1", 8080)
    .bind(route)

  println(s"Server now online. Please navigate to http://localhost:8080/akka-http-tapir/1\nPress RETURN to stop...")
  StdIn.readLine()
  bindingFuture
    .flatMap(_.unbind())
    .onComplete(_ => actorSystem.terminate())
}
