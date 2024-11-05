// {cat=Security; effects=Future; server=Pekko HTTP}: Separating security and server logic, with a reusable base endpoint, accepting & refreshing credentials via cookies

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-pekko-http-server:1.11.8
//> using dep com.softwaremill.sttp.client3::core:3.9.8

package sttp.tapir.examples.security

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.server.Directives.concat
import org.apache.pekko.http.scaladsl.server.Route
import sttp.client3.*
import sttp.shared.Identity
import sttp.model.StatusCode
import sttp.model.headers.CookieValueWithMeta
import sttp.tapir.*
import sttp.tapir.server.pekkohttp.PekkoHttpServerInterpreter
import sttp.tapir.server.{PartialServerEndpointWithSecurityOutput, ServerEndpoint}

import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}

@main def serverSecurityLogicRefreshCookiesPekko(): Unit =
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
  val helloWorld1Route: Route = PekkoHttpServerInterpreter().toRoute(secureHelloWorldWithLogic)

  // starting the server
  val bindAndCheck = Http().newServerAt("localhost", 8080).bind(concat(helloWorld1Route)).map { binding =>
    // testing
    val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()

    val response = basicRequest
      .response(asStringAlways)
      .cookie("token", "Steve")
      .get(uri"http://localhost:8080/hello?salutation=Welcome")
      .send(backend)

    assert(response.body == "Welcome, Steve!")
    assert(response.unsafeCookies.map(_.value).toList == List("new token"))

    binding
  }

  val _ = Await.result(bindAndCheck.flatMap(_.terminate(1.minute)), 1.minute)
