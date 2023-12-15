package sttp.tapir.examples2.security

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import sttp.client3._
import sttp.model.StatusCode
import sttp.model.headers.WWWAuthenticateChallenge
import sttp.tapir._
import sttp.tapir.model._
import sttp.tapir.server.akkahttp._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object BasicAuthenticationAkkaServer extends App {
  implicit val actorSystem: ActorSystem = ActorSystem()
  import actorSystem.dispatcher

  val secret: Endpoint[UsernamePassword, Unit, Unit, String, Any] =
    endpoint.get.securityIn("secret").securityIn(auth.basic[UsernamePassword](WWWAuthenticateChallenge.basic("example"))).out(stringBody)

  val secretRoute: Route =
    AkkaHttpServerInterpreter().toRoute(
      secret
        .serverSecurityLogic(credentials => Future.successful(Right(credentials.username): Either[Unit, String]))
        .serverLogic(username => _ => Future.successful(Right(s"Hello, $username!")))
    )

  // starting the server
  val bindAndCheck = Http().newServerAt("localhost", 8080).bindFlow(secretRoute).map { _ =>
    // testing
    val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()
    val unauthorized = basicRequest.get(uri"http://localhost:8080/secret").send(backend)
    println("Got result: " + unauthorized)
    assert(unauthorized.code == StatusCode.Unauthorized)
    assert(unauthorized.header("WWW-Authenticate").contains("""Basic realm="example""""))

    val result = basicRequest.get(uri"http://localhost:8080/secret").header("Authorization", "Basic dXNlcjpzZWNyZXQ=").send(backend)
    println("Got result: " + result)
    assert(result.code == StatusCode.Ok)
    assert(result.body == Right("Hello, user!"))
  }

  Await.result(bindAndCheck.transformWith { r => actorSystem.terminate().transform(_ => r) }, 1.minute)
}
