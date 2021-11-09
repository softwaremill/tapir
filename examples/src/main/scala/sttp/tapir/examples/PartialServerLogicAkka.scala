package sttp.tapir.examples

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import sttp.client3._
import sttp.tapir._
import sttp.tapir.server.{PartialServerEndpoint, ServerEndpoint}
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object PartialServerLogicAkka extends App {
  implicit val actorSystem: ActorSystem = ActorSystem()
  import actorSystem.dispatcher

  // authentication logic
  case class User(name: String)
  val AuthenticationErrorCode = 1001
  def auth(token: String): Future[Either[Int, User]] =
    Future {
      if (token == "secret") Right(User("Spock"))
      else Left(AuthenticationErrorCode)
    }

  // 1st approach: define a base endpoint, which has the authentication logic built-in
  val secureEndpoint: PartialServerEndpoint[String, User, Unit, Int, Unit, Any, Future] = endpoint
    .securityIn(header[String]("X-AUTH-TOKEN"))
    .errorOut(plainBody[Int])
    .serverSecurityLogic(auth)

  // extend the base endpoint to define (potentially multiple) proper endpoints, define the rest of the server logic
  val secureHelloWorld1WithLogic: ServerEndpoint[Any, Future] = secureEndpoint.get
    .in("hello1")
    .in(query[String]("salutation"))
    .out(stringBody)
    .serverLogic { user => salutation => Future(Right(s"$salutation, ${user.name}!")) }

  // ---

  // interpreting as routes
  val helloWorld1Route: Route = AkkaHttpServerInterpreter().toRoute(secureHelloWorld1WithLogic)
  // TODO val helloWorld2Route: Route = AkkaHttpServerInterpreter().toRoute(secureHelloWorld2WithLogic)

  // starting the server
  val bindAndCheck = Http().newServerAt("localhost", 8080).bind(concat(helloWorld1Route)).map { _ =>
    // testing
    val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()

    def testWith(path: String, salutation: String, token: String): String = {
      val result: String = basicRequest
        .response(asStringAlways)
        .get(uri"http://localhost:8080/$path?salutation=$salutation")
        .header("X-AUTH-TOKEN", token)
        .send(backend)
        .body

      println(s"For path: $path, salutation: $salutation, token: $token, got result: $result")
      result
    }

    assert(testWith("hello1", "Hello", "secret") == "Hello, Spock!")
    assert(testWith("hello2", "Hello", "secret") == "Hello, Spock!")
    assert(testWith("hello1", "Cześć", "secret") == "Cześć, Spock!")
    assert(testWith("hello2", "Cześć", "secret") == "Cześć, Spock!")
    assert(testWith("hello1", "Hello", "1234") == AuthenticationErrorCode.toString)
    assert(testWith("hello2", "Hello", "1234") == AuthenticationErrorCode.toString)
  }

  Await.result(bindAndCheck.transformWith { r => actorSystem.terminate().transform(_ => r) }, 1.minute)
}
