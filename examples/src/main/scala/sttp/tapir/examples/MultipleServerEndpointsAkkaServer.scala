package sttp.tapir.examples

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import sttp.client3._
import sttp.tapir._
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object MultipleServerEndpointsAkkaServer extends App {
  implicit val actorSystem: ActorSystem = ActorSystem()
  import actorSystem.dispatcher

  // endpoint descriptions, together with the server logic
  val endpoint1 = endpoint.get.in("endpoint1").out(stringBody).serverLogicSuccess { _ => Future.successful("ok1") }
  val endpoint2 =
    endpoint.get.in("endpoint2").in(path[String]).out(stringBody).serverLogicSuccess { path => Future.successful(s"ok2: $path") }

  // converting the endpoints to a (single) route
  val route: Route = AkkaHttpServerInterpreter().toRoute(List(endpoint1, endpoint2))

  // starting the server
  val bindAndCheck = Http().newServerAt("localhost", 8080).bindFlow(route).map { _ =>
    // testing
    val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()

    val result1: String = basicRequest.response(asStringAlways).get(uri"http://localhost:8080/endpoint1").send(backend).body
    println("Got result (1): " + result1)
    assert(result1 == "ok1")

    val result2: String = basicRequest.response(asStringAlways).get(uri"http://localhost:8080/endpoint2/apple").send(backend).body
    println("Got result (2): " + result2)
    assert(result2 == "ok2: apple")
  }

  Await.result(bindAndCheck.transformWith { r => actorSystem.terminate().transform(_ => r) }, 1.minute)
}
