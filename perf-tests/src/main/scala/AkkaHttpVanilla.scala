package perfTests

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._

class AkkaHttpVanillaMultiServer {
  implicit val actorSystem = ActorSystem(Behaviors.empty, "akka-http")
  implicit val executionContext = actorSystem.executionContext
  var bindingFuture: Option[scala.concurrent.Future[akka.http.scaladsl.Http.ServerBinding]] = None

  def setUp(nRoutes: Int) = {
    val route = (n: Int) => get {
      path(("path" + n.toString) / IntNumber) {
        id => complete(id.toString)
      }
    }

    this.bindingFuture = Some(
      Http()
        .newServerAt("127.0.0.1", 8080)
        .bind(
          Range(1, nRoutes, 1)
            .foldLeft(route(0))(
              (acc, n) => concat(acc, route(n)))
        )
    )
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

object AkkaHttpVanillaMultiServer extends App {
  var server = new perfTests.AkkaHttpVanillaMultiServer()
  server.setUp(128)
  Common.blockServer()
  server.tearDown()
  println("Server terminated")
}

object AkkaHttpVanillaServer extends App {
  var server = new perfTests.AkkaHttpVanillaMultiServer()
  server.setUp(1)
  Common.blockServer()
  server.tearDown()
  println("Server terminated")
}
