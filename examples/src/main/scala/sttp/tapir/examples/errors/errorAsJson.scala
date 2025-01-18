// {cat=Error handling; effects=Future; server=Pekko HTTP; json=circe}: Default error handler returning errors as JSON

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.12
//> using dep com.softwaremill.sttp.tapir::tapir-pekko-http-server:1.11.12
//> using dep com.softwaremill.sttp.tapir::tapir-json-circe:1.11.12
//> using dep org.apache.pekko::pekko-http:1.0.1
//> using dep org.apache.pekko::pekko-stream:1.0.3
//> using dep com.softwaremill.sttp.client3::core:3.10.2
//> using dep com.softwaremill.sttp.client3::circe:3.10.2

package sttp.tapir.examples.errors

import io.circe.generic.auto.*
import io.circe.parser
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.server.Route
import sttp.client3.*
import sttp.client3.circe.*
import sttp.model.StatusCode
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.model.ValuedEndpointOutput
import sttp.tapir.server.pekkohttp.*

import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}

@main def errorAsJson(): Unit =
  implicit val actorSystem: ActorSystem = ActorSystem()
  import actorSystem.dispatcher

  enum Severity:
    case Trace, Debug, Info, Warning, Error, Fatal
  
  case class Error(severity: Severity, message: String)

  case class Person(name: String, surname: String, age: Int)

  // the endpoint description
  val errorJson: PublicEndpoint[Person, Unit, String, Any] =
    endpoint.post
      .in("person" / jsonBody[Person])
      .out(stringBody)

  // By default, convert all String errors to Error case class with severity "Error"
  val options = PekkoHttpServerOptions.customiseInterceptors.defaultHandlers(err => ValuedEndpointOutput(jsonBody[Error], Error(Severity.Error, err))).options

  // converting an endpoint to a route
  val errorOrJsonRoute: Route = PekkoHttpServerInterpreter(options).toRoute(errorJson.serverLogic {
    case person if person.age < 18 => throw new RuntimeException("Oops, something went wrong in the server internals!")
    case x          => Future.successful(Right("Operation successful"))
  })

  // starting the server
  val bindAndCheck = Http().newServerAt("localhost", 8080).bindFlow(errorOrJsonRoute).map { binding =>
    // testing
    val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()

    // This causes internal exception, resulting in response status code 500
    val response1 = basicRequest.post(uri"http://localhost:8080/person").body(Person("Pawel", "Stawicki", 4)).send(backend)
    assert(response1.code == StatusCode.InternalServerError)
    val result1: Either[String, String] = response1.body
    println("Got result (1): " + result1)
    // Response body contains Error case class serialized to JSON
    // Mind the "swap" - this Either was originally Left, but we need to swap it in order to parse it later
    val error1 = result1.swap.flatMap(parser.parse).flatMap(_.as[Error])
    assert(error1 == Right(Error(Severity.Error, "Internal server error")))

    // Bad request sent, resulting in response status code 400
    val response2 = basicRequest.post(uri"http://localhost:8080/person").body("invalid json").send(backend)
    assert(response2.code == StatusCode.BadRequest)
    val result2: Either[String, String] = response2.body
    println("Got result (2): " + result2)
    val error2 = result2.swap.flatMap(parser.parse).flatMap(_.as[Error])
    assert(error2 == Right(Error(Severity.Error, "Invalid value for: body (expected json value got 'invali...' (line 1, column 1))")))

    val result3: Either[String, String] = basicRequest.post(uri"http://localhost:8080/person").body(Person("Pawel", "Stawicki", 46)).send(backend).body
    println("Got result (3): " + result3)
    assert(result3 == Right("Operation successful"))

    binding
  }

  val _ = Await.result(bindAndCheck.flatMap(_.terminate(1.minute)), 1.minute)
