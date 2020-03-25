package sttp.tapir.examples

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import sttp.tapir._
import sttp.tapir.server.akkahttp._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import sttp.client._

object HelloWorldAkkaServer extends App {
  // the endpoint: single fixed path input ("hello"), single query parameter
  // corresponds to: GET /hello?name=...
  val helloWorld: Endpoint[String, Unit, String, Nothing] =
    endpoint.get.in("hello").in(query[String]("name")).out(stringBody)

  // converting an endpoint to a route (providing server-side logic); extension method comes from imported packages
  val helloWorldRoute: Route = helloWorld.toRoute(name => Future.successful(Right(s"Hello, $name!")))

  // starting the server
  implicit val actorSystem: ActorSystem = ActorSystem()
  import actorSystem.dispatcher

  val bindAndCheck = Http().bindAndHandle(helloWorldRoute, "localhost", 8080).map { _ =>
    // testing
    implicit val backend: SttpBackend[Identity, Nothing, NothingT] = HttpURLConnectionBackend()
    val result: String = basicRequest.response(asStringAlways).get(uri"http://localhost:8080/hello?name=Frodo").send().body
    println("Got result: " + result)

    assert(result == "Hello, Frodo!")
  }

  Await.result(bindAndCheck.transformWith { r => actorSystem.terminate().transform(_ => r) }, 1.minute)
}
