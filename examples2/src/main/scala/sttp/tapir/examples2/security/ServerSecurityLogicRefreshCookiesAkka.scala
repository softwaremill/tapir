package sttp.tapir.examples2.security

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.concat
import akka.http.scaladsl.server.Route
import sttp.client3._
import sttp.model.StatusCode
import sttp.model.headers.CookieValueWithMeta
import sttp.tapir._
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter
import sttp.tapir.server.{PartialServerEndpointWithSecurityOutput, ServerEndpoint}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object ServerSecurityLogicRefreshCookiesAkka extends App {
  implicit val actorSystem: ActorSystem = ActorSystem()
  import actorSystem.dispatcher

  case class User(name: String)

  // we're always refreshing the cookie
  def authenticate(token: Option[String]): Either[Unit, (Option[CookieValueWithMeta], User)] =
    token match {
      case None        => Left(())
      case Some(token) => Right((Some(CookieValueWithMeta.unsafeApply("new token")), User(token)))
    }

  // defining a base endpoint, which has the authentication logic built-in
  val secureEndpoint
      : PartialServerEndpointWithSecurityOutput[Option[String], User, Unit, Unit, Option[CookieValueWithMeta], Unit, Any, Future] = endpoint
    .securityIn(auth.apiKey(cookie[Option[String]]("token")))
    // optionally returning a refreshed cookie
    .out(setCookieOpt("token"))
    .errorOut(statusCode(StatusCode.Unauthorized))
    .serverSecurityLogicPureWithOutput(authenticate)

  // extending the base endpoint with hello-endpoint-specific inputs
  val secureHelloWorldWithLogic: ServerEndpoint[Any, Future] = secureEndpoint.get
    .in("hello")
    .in(query[String]("salutation"))
    .out(stringBody)
    // defining the remaining server logic (which uses the authenticated user)
    .serverLogic { user => salutation =>
      Future(Right(s"$salutation, ${user.name}!"))
    }

  // ---

  // interpreting as routes
  val helloWorld1Route: Route = AkkaHttpServerInterpreter().toRoute(secureHelloWorldWithLogic)

  // starting the server
  val bindAndCheck = Http().newServerAt("localhost", 8080).bind(concat(helloWorld1Route)).map { _ =>
    // testing
    val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()

    val response = basicRequest
      .response(asStringAlways)
      .cookie("token", "Steve")
      .get(uri"http://localhost:8080/hello?salutation=Welcome")
      .send(backend)

    assert(response.body == "Welcome, Steve!")
    assert(response.unsafeCookies.map(_.value).toList == List("new token"))
  }

  Await.result(bindAndCheck.transformWith { r => actorSystem.terminate().transform(_ => r) }, 1.minute)
}
