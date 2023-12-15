package sttp.tapir.examples3.errors

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.server.Route
import sttp.client3.*
import sttp.tapir.generic.auto.*
import sttp.tapir.*
import sttp.tapir.server.pekkohttp.*
import sttp.tapir.json.circe.*
import io.circe.generic.auto.*

import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}

object ErrorOutputsPekkoServer extends App {
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
  val errorOrJsonRoute: Route = PekkoHttpServerInterpreter().toRoute(errorOrJson.serverLogic {
    case x if x < 0 => Future.successful(Left("Invalid parameter, smaller than 0!"))
    case x          => Future.successful(Right(Result(x * 2)))
  })

  // starting the server
  val bindAndCheck = Http().newServerAt("localhost", 8080).bindFlow(errorOrJsonRoute).map { binding =>
    // testing
    val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()

    val result1: Either[String, String] = basicRequest.get(uri"http://localhost:8080?amount=-5").send(backend).body
    println("Got result (1): " + result1)
    assert(result1 == Left("Invalid parameter, smaller than 0!"))

    val result2: Either[String, String] = basicRequest.get(uri"http://localhost:8080?amount=21").send(backend).body
    println("Got result (2): " + result2)
    assert(result2 == Right("""{"result":42}"""))

    binding
  }

  Await.result(bindAndCheck.flatMap(_.terminate(1.minute)), 1.minute)
}
