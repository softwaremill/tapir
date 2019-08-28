package tapir.examples

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import com.softwaremill.sttp._
import tapir._
import tapir.server.akkahttp._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object MultipleServerEndpointsAkkaServer extends App {
  // endpoint descriptions, together with the server logic
  val endpoint1 = endpoint.get.in("endpoint1").out(stringBody).serverLogic[Future] { _ =>
    Future.successful(Right("ok1"))
  }
  val endpoint2 = endpoint.get.in("endpoint2").in(path[String]).out(stringBody).serverLogic[Future] { path =>
    Future.successful(Right(s"ok2: $path"))
  }

  // converting the endpoints to a (single) route
  val route: Route = List(endpoint1, endpoint2).toRoute

  // starting the server
  implicit val actorSystem: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  try {
    Await.result(Http().bindAndHandle(route, "localhost", 8080), 1.minute)

    // testing
    implicit val backend: SttpBackend[Id, Nothing] = HttpURLConnectionBackend()

    val result1: String = sttp.get(uri"http://localhost:8080/endpoint1").send().unsafeBody
    println("Got result (1): " + result1)
    assert(result1 == "ok1")

    val result2: String = sttp.get(uri"http://localhost:8080/endpoint2/apple").send().unsafeBody
    println("Got result (2): " + result2)
    assert(result2 == "ok2: apple")
  } finally {
    // cleanup
    actorSystem.terminate()
  }
}
