package sttp.tapir.examples

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import sttp.client3._
import sttp.model.HeaderNames
import sttp.tapir._
import sttp.tapir.server.{PartialServerEndpoint, ServerEndpoint}
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object ServerSecurityLogicAkka extends App {
  implicit val actorSystem: ActorSystem = ActorSystem()
  import actorSystem.dispatcher

  // authentication logic
  case class User(name: String)
  case class AuthenticationToken(value: String)
  case class AuthenticationException(code: Int) extends Exception

  def authenticate(token: AuthenticationToken): Future[Either[AuthenticationException, User]] =
    Future {
      if (token.value == "secret") Right(User("Spock"))
      else Left(AuthenticationException(1001))
    }

  // define a base endpoint, which has the authentication logic built-in
  val secureEndpoint: PartialServerEndpoint[AuthenticationToken, User, Unit, AuthenticationException, Unit, Any, Future] = endpoint
    .securityIn(auth.bearer[String]().mapTo[AuthenticationToken])
    .errorOut(plainBody[Int].mapTo[AuthenticationException])
    .serverSecurityLogic(authenticate)

  // extend the base endpoint to define (potentially multiple) proper endpoints, define the rest of the server logic
  case class HelloException(why: String) extends Exception

  val secureHelloWorldWithLogic: ServerEndpoint[Any, Future] = secureEndpoint.get
    .in("hello")
    .in(query[String]("salutation"))
    .out(stringBody)
    .errorOutVariant(oneOfVariant(stringBody.mapTo[HelloException]))
    .serverLogic { user => salutation => Future(Right(s"$salutation, ${user.name}!")) }

  // ---

  // interpreting as routes
  val helloWorld1Route: Route = AkkaHttpServerInterpreter().toRoute(secureHelloWorldWithLogic)

  // starting the server
  val bindAndCheck = Http().newServerAt("localhost", 8080).bind(concat(helloWorld1Route)).map { _ =>
    // testing
    val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()

    def testWith(path: String, salutation: String, token: String): String = {
      val result: String = basicRequest
        .response(asStringAlways)
        .get(uri"http://localhost:8080/$path?salutation=$salutation")
        .header(HeaderNames.Authorization, s"Bearer $token")
        .send(backend)
        .body

      println(s"For path: $path, salutation: $salutation, token: $token, got result: $result")
      result
    }

    assert(testWith("hello", "Hello", "secret") == "Hello, Spock!")
    assert(testWith("hello", "Cześć", "secret") == "Cześć, Spock!")
    assert(testWith("hello", "Hello", "1234") == "1001")
  }

  Await.result(bindAndCheck.transformWith { r => actorSystem.terminate().transform(_ => r) }, 1.minute)
}
