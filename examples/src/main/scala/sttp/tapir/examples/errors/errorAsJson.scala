// {cat=Error handling; effects=Future; server=Pekko HTTP; json=circe}: Default error handler returning errors as JSON

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.39
//> using dep com.softwaremill.sttp.tapir::tapir-pekko-http-server:1.11.39
//> using dep com.softwaremill.sttp.tapir::tapir-json-circe:1.11.39
//> using dep org.apache.pekko::pekko-http:1.0.1
//> using dep org.apache.pekko::pekko-stream:1.0.3
//> using dep com.softwaremill.sttp.client4::core:4.0.0-RC3
//> using dep com.softwaremill.sttp.client4::circe:4.0.9

package sttp.tapir.examples.errors

import io.circe.generic.auto.*
import io.circe.parser
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.server.Route
import sttp.client4.*
import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.client4.circe.*
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.model.ValuedEndpointOutput
import sttp.tapir.server.pekkohttp.*

import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}

@main def errorAsJson(): Unit =
  given actorSystem: ActorSystem = ActorSystem()
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
  val options = PekkoHttpServerOptions.customiseInterceptors
    .defaultHandlers(err => ValuedEndpointOutput(jsonBody[Error], Error(Severity.Error, err)))
    .options

  // converting an endpoint to a route
  val errorOrJsonRoute: Route = PekkoHttpServerInterpreter(options).toRoute(errorJson.serverLogic {
    case person if person.age < 18 => throw new RuntimeException("Oops, something went wrong in the server internals!")
    case x                         => Future.successful(Right("Operation successful"))
  })

  // starting the server
  val bindAndCheck = Http().newServerAt("localhost", 8080).bindFlow(errorOrJsonRoute).map { binding =>
    // testing
    val backend: SyncBackend = HttpClientSyncBackend()

    // This causes internal exception, resulting in response status code 500
    val response1 =
      basicRequest
        .post(uri"http://localhost:8080/person")
        .body(asJson(Person("Pawel", "Stawicki", 4)))
        .response(asStringAlways)
        .send(backend)
    assert(response1.code == StatusCode.InternalServerError)
    println("Got result (1): " + response1.body)
    // Response body contains Error case class serialized to JSON
    val error1 = parser.parse(response1.body).flatMap(_.as[Error])
    assert(error1 == Right(Error(Severity.Error, "Internal server error")))

    // Bad request sent, resulting in response status code 400
    val response2 = basicRequest.post(uri"http://localhost:8080/person").body("invalid json").response(asStringAlways).send(backend)
    assert(response2.code == StatusCode.BadRequest)
    println("Got result (2): " + response2.body)
    val error2 = parser.parse(response2.body).flatMap(_.as[Error])
    assert(error2 == Right(Error(Severity.Error, "Invalid value for: body (expected json value got 'invali...' (line 1, column 1))")))

    val response3 =
      basicRequest
        .post(uri"http://localhost:8080/person")
        .body(asJson(Person("Pawel", "Stawicki", 46)))
        .response(asStringAlways)
        .send(backend)
    println("Got result (3): " + response3.body)
    assert(response3.body == "Operation successful")

    binding
  }

  try
    val _ = Await.result(bindAndCheck.flatMap(_.terminate(1.minute)), 1.minute)
  finally
    val _ = actorSystem.terminate()
