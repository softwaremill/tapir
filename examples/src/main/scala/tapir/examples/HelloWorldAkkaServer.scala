package tapir.examples

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import tapir._
import tapir.server.akkahttp._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

import com.softwaremill.sttp._

object HelloWorldAkkaServer extends App {
  // the endpoint: single fixed path input ("hello"), single query parameter
  // corresponds to: GET /hello?name=...
  val helloWorld: Endpoint[String, Unit, String, Nothing] =
    endpoint.get.in("hello").in(query[String]("name")).out(stringBody)

  // converting an endpoint to a route (providing server-side logic); extension method comes from imported packages
  val helloWorldRoute: Route = helloWorld.toRoute(name => Future.successful(Right(s"Hello, $name!")))

  // starting the server
  implicit val actorSystem: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  try {
    Await.result(Http().bindAndHandle(helloWorldRoute, "localhost", 8080), 1.minute)

    // testing
    implicit val backend: SttpBackend[Id, Nothing] = HttpURLConnectionBackend()
    val result: String = sttp.get(uri"http://localhost:8080/hello?name=Frodo").send().unsafeBody
    println("Got result: " + result)

    assert(result == "Hello, Frodo!")
  } finally {
    // cleanup
    actorSystem.terminate()
  }
}
