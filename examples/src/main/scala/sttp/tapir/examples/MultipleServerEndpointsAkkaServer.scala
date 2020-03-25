package sttp.tapir.examples

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import sttp.client._
import sttp.tapir._
import sttp.tapir.server.akkahttp._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object MultipleServerEndpointsAkkaServer extends App {
  // endpoint descriptions, together with the server logic
  val endpoint1 = endpoint.get.in("endpoint1").out(stringBody).serverLogic[Future] { _ => Future.successful(Right("ok1")) }
  val endpoint2 =
    endpoint.get.in("endpoint2").in(path[String]).out(stringBody).serverLogic[Future] { path => Future.successful(Right(s"ok2: $path")) }

  // converting the endpoints to a (single) route
  val route: Route = List(endpoint1, endpoint2).toRoute

  // starting the server
  implicit val actorSystem: ActorSystem = ActorSystem()
  import actorSystem.dispatcher

  val bindAndCheck = Http().bindAndHandle(route, "localhost", 8080).map { _ =>
    // testing
    implicit val backend: SttpBackend[Identity, Nothing, NothingT] = HttpURLConnectionBackend()

    val result1: String = basicRequest.response(asStringAlways).get(uri"http://localhost:8080/endpoint1").send().body
    println("Got result (1): " + result1)
    assert(result1 == "ok1")

    val result2: String = basicRequest.response(asStringAlways).get(uri"http://localhost:8080/endpoint2/apple").send().body
    println("Got result (2): " + result2)
    assert(result2 == "ok2: apple")
  }

  Await.result(bindAndCheck.transformWith { r => actorSystem.terminate().transform(_ => r) }, 1.minute)
}
