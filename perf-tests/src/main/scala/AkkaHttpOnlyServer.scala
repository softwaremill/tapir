package perfTests

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._

import scala.io.StdIn

class AkkaHttpOnlyServer {
  implicit val actorSystem = ActorSystem(Behaviors.empty, "akka-http")
  implicit val executionContext = actorSystem.executionContext
  var bindingFuture: Option[scala.concurrent.Future[akka.http.scaladsl.Http.ServerBinding]] = None

  def setUp() = {
    val route = get {
      path("akka-http-only" / IntNumber) {
        id => complete(id.toString)
      }
    }

    bindingFuture = Some(Http()
      .newServerAt("127.0.0.1", 8080)
      .bind(route))
  }

  def tearDown() = {
    bindingFuture match {
      case Some(b) => {
        b
          .flatMap(_.unbind())
          .onComplete(_ => actorSystem.terminate())
      }
      case None => ()
    }
  }
}

object AkkaHttpOnlyServer extends App {
  var server = new perfTests.AkkaHttpOnlyServer()
  server.setUp()
  println(s"Server now online. Please navigate to http://localhost:8080/akka-http-only/1\nPress RETURN to stop...")
  StdIn.readLine()
  server.tearDown()
  println(s"Server terminated")
}
