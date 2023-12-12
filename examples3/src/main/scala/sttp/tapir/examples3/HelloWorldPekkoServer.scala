package sttp.tapir.examples3

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.server.Route
import sttp.client3.*
import sttp.tapir.*
import sttp.tapir.server.pekkohttp.PekkoHttpServerInterpreter

import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}

object HelloWorldPekkoServer extends App {
  implicit val actorSystem: ActorSystem = ActorSystem()
  import actorSystem.dispatcher

  // the endpoint: single fixed path input ("hello"), single query parameter
  // corresponds to: GET /hello?name=...
  val helloWorld: PublicEndpoint[String, Unit, String, Any] =
    endpoint.get.in("hello").in(query[String]("name")).out(stringBody)

  // converting an endpoint to a route (providing server-side logic); extension method comes from imported packages
  val helloWorldRoute: Route =
    PekkoHttpServerInterpreter().toRoute(helloWorld.serverLogicSuccess(name => Future.successful(s"Hello, $name!")))

  // starting the server
  val bindAndCheck = Http().newServerAt("localhost", 8080).bindFlow(helloWorldRoute).map { _ =>
    // testing
    val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()
    val result: String = basicRequest.response(asStringAlways).get(uri"http://localhost:8080/hello?name=Frodo").send(backend).body
    println("Got result: " + result)

    assert(result == "Hello, Frodo!")
  }

  Await.result(bindAndCheck.transformWith { r => actorSystem.terminate().transform(_ => r) }, 1.minute)
}
