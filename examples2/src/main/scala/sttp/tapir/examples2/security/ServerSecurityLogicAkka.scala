package sttp.tapir.examples2.security

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

  // authentication data structure & logic
  case class User(name: String)
  case class AuthenticationToken(value: String)
  case class AuthenticationError(code: Int)

  def authenticate(token: AuthenticationToken): Future[Either[AuthenticationError, User]] =
    Future {
      if (token.value == "berries") Right(User("Papa Smurf"))
      else if (token.value == "smurf") Right(User("Gargamel"))
      else Left(AuthenticationError(1001))
    }

  // defining a base endpoint, which has the authentication logic built-in
  val secureEndpoint: PartialServerEndpoint[AuthenticationToken, User, Unit, AuthenticationError, Unit, Any, Future] = endpoint
    .securityIn(auth.bearer[String]().mapTo[AuthenticationToken])
    // returning the authentication error code to the user
    .errorOut(plainBody[Int].mapTo[AuthenticationError])
    .serverSecurityLogic(authenticate)

  // the errors that might occur in the /hello endpoint - either a wrapped authentication error, or refusal to greet
  sealed trait HelloError
  case class AuthenticationHelloError(wrapped: AuthenticationError) extends HelloError
  case class NoHelloError(why: String) extends HelloError

  // extending the base endpoint with hello-endpoint-specific inputs
  val secureHelloWorldWithLogic: ServerEndpoint[Any, Future] = secureEndpoint.get
    .in("hello")
    .in(query[String]("salutation"))
    .out(stringBody)
    .mapErrorOut(AuthenticationHelloError)(_.wrapped)
    // returning a 400 with the "why" field from the exception
    .errorOutVariant[HelloError](oneOfVariant(stringBody.mapTo[NoHelloError]))
    // defining the remaining server logic (which uses the authenticated user)
    .serverLogic { user => salutation =>
      Future(
        if (user.name == "Gargamel") Left(NoHelloError(s"Not saying hello to ${user.name}!"))
        else Right(s"$salutation, ${user.name}!")
      )
    }

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

    assert(testWith("hello", "Hello", "berries") == "Hello, Papa Smurf!")
    assert(testWith("hello", "Cześć", "berries") == "Cześć, Papa Smurf!")
    assert(testWith("hello", "Hello", "apple") == "1001")
    assert(testWith("hello", "Hello", "smurf") == "Not saying hello to Gargamel!")
  }

  Await.result(bindAndCheck.transformWith { r => actorSystem.terminate().transform(_ => r) }, 1.minute)
}
