import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._

import scala.io.StdIn

final case class Book(id: Int)

object AkkaHttpOnlyServer extends App {
  implicit val actorSystem = ActorSystem(Behaviors.empty, "akka-http")
  implicit val executionContext = actorSystem.executionContext

  val route = get {
    path("akka-http-only" / IntNumber) {
      id => complete(id.toString)
    }
  }

  val bindingFuture = Http()
    .newServerAt("127.0.0.1", 8080)
    .bind(route)

  println(s"Server now online. Please navigate to http://localhost:8080/akka-http-only/1\nPress RETURN to stop...")
  StdIn.readLine()
  bindingFuture
    .flatMap(_.unbind())
    .onComplete(_ => actorSystem.terminate())
}
