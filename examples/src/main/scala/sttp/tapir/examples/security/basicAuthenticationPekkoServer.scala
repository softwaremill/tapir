// {cat=Security; effects=Future; server=Pekko HTTP}: HTTP basic authentication

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.12.1
//> using dep com.softwaremill.sttp.tapir::tapir-pekko-http-server:1.12.1
//> using dep com.softwaremill.sttp.client4::core:4.0.0-RC3

package sttp.tapir.examples.security

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.server.Route
import sttp.client4.*
import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.model.StatusCode
import sttp.model.headers.WWWAuthenticateChallenge
import sttp.tapir.*
import sttp.tapir.model.*
import sttp.tapir.server.pekkohttp.*

import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}

@main def basicAuthenticationPekkoServer(): Unit =
  implicit val actorSystem: ActorSystem = ActorSystem()
  import actorSystem.dispatcher

  val secret: Endpoint[UsernamePassword, Unit, Unit, String, Any] =
    endpoint.get.securityIn("secret").securityIn(auth.basic[UsernamePassword](WWWAuthenticateChallenge.basic("example"))).out(stringBody)

  val secretRoute: Route =
    PekkoHttpServerInterpreter().toRoute(
      secret
        .serverSecurityLogic(credentials => Future.successful(Right(credentials.username): Either[Unit, String]))
        .serverLogic(username => _ => Future.successful(Right(s"Hello, $username!")))
    )

  // starting the server
  val bindAndCheck = Http().newServerAt("localhost", 8080).bindFlow(secretRoute).map { binding =>
    // testing
    val backend: SyncBackend = HttpClientSyncBackend()
    val unauthorized = basicRequest.get(uri"http://localhost:8080/secret").send(backend)
    println("Got result: " + unauthorized)
    assert(unauthorized.code == StatusCode.Unauthorized)
    assert(unauthorized.header("WWW-Authenticate").contains("""Basic realm="example""""))

    val result = basicRequest.get(uri"http://localhost:8080/secret").header("Authorization", "Basic dXNlcjpzZWNyZXQ=").send(backend)
    println("Got result: " + result)
    assert(result.code == StatusCode.Ok)
    assert(result.body == Right("Hello, user!"))

    binding
  }

  val _ = Await.result(bindAndCheck.flatMap(_.terminate(1.minute)), 1.minute)
