package perfTests

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route

import sttp.tapir._
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter

import scala.io.StdIn
import scala.concurrent.Future

class AkkaHttpTapirServer {
  implicit val actorSystem = ActorSystem(Behaviors.empty, "akka-http")
  implicit val executionContext = actorSystem.executionContext

  var bindingFuture: Option[scala.concurrent.Future[akka.http.scaladsl.Http.ServerBinding]] = None

  def setUp() = {
    val bookEndpoint: PublicEndpoint[(Int), String, String, Any] = endpoint
      .get
      .in("akka-http-tapir")
      .in(path[Int]("id"))
      .errorOut(stringBody)
      .out(stringBody)

    def bookEndpointLogic(id: Int): Future[Either[String, String]] =
      Future.successful(Right(id.toString))

    val route: Route = AkkaHttpServerInterpreter()
      .toRoute(bookEndpoint.serverLogic(bookEndpointLogic))

    this.bindingFuture = Some(Http()
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

object AkkaHttpTapirServer extends App {
  var server = new perfTests.AkkaHttpTapirServer()
  server.setUp()
  println(s"Server now online. Please navigate to http://localhost:8080/akka-http-tapir/1\nPress RETURN to stop...")
  StdIn.readLine()
  server.tearDown()
  println(s"Server terminated")
}
