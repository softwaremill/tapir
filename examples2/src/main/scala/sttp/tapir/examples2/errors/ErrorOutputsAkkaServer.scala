package sttp.tapir.examples2.errors

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import sttp.client3._
import sttp.tapir.generic.auto._
import sttp.tapir._
import sttp.tapir.server.akkahttp._
import sttp.tapir.json.circe._
import io.circe.generic.auto._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object ErrorOutputsAkkaServer extends App {
  implicit val actorSystem: ActorSystem = ActorSystem()
  import actorSystem.dispatcher

  // the endpoint description
  case class Result(result: Int)

  val errorOrJson: PublicEndpoint[Int, String, Result, Any] =
    endpoint.get
      .in(query[Int]("amount"))
      .out(jsonBody[Result])
      .errorOut(stringBody)

  // converting an endpoint to a route
  val errorOrJsonRoute: Route = AkkaHttpServerInterpreter().toRoute(errorOrJson.serverLogic {
    case x if x < 0 => Future.successful(Left("Invalid parameter, smaller than 0!"))
    case x          => Future.successful(Right(Result(x * 2)))
  })

  // starting the server
  val bindAndCheck = Http().newServerAt("localhost", 8080).bindFlow(errorOrJsonRoute).map { _ =>
    // testing
    val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()

    val result1: Either[String, String] = basicRequest.get(uri"http://localhost:8080?amount=-5").send(backend).body
    println("Got result (1): " + result1)
    assert(result1 == Left("Invalid parameter, smaller than 0!"))

    val result2: Either[String, String] = basicRequest.get(uri"http://localhost:8080?amount=21").send(backend).body
    println("Got result (2): " + result2)
    assert(result2 == Right("""{"result":42}"""))
  }

  Await.result(bindAndCheck.transformWith { r => actorSystem.terminate().transform(_ => r) }, 1.minute)
}
