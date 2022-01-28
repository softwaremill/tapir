package sttp.tapir.examples

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import sttp.client3._
import sttp.model.StatusCode
import sttp.monad.FutureMonad
import sttp.tapir._
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter
import sttp.tapir.static.StaticErrorOutput

import java.nio.file.{Files, Path, StandardOpenOption}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

object StaticContentSecureAkkaServer extends App {
  // creating test files
  val exampleDirectory: Path = Files.createTempDirectory("akka-static-secure-example")
  Files.write(exampleDirectory.resolve("f1"), "f1 content".getBytes, StandardOpenOption.CREATE_NEW)

  private implicit val actorSystem: ActorSystem = ActorSystem()
  import actorSystem.dispatcher

  // defining the endpoint
  val fileEndpoint = filesGetEndpoint
    .securityIn("secure".and(auth.bearer[String]()))
    .serverSecurityLogicPure(token => if (token.startsWith("secret")) Right(token) else Left(StaticErrorOutput.NotFound))
    .serverLogic(_ => sttp.tapir.static.Files.get[Future](exampleDirectory.toFile.getAbsolutePath)(new FutureMonad))

  // starting the server
  private val route: Route = AkkaHttpServerInterpreter().toRoute(fileEndpoint)

  private val bindAndCheck: Future[Unit] = Http().newServerAt("localhost", 8080).bindFlow(route).map { _ =>
    // testing
    val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()
    val response1 = basicRequest
      .get(uri"http://localhost:8080/secure/f1")
      .auth
      .bearer("hacker")
      .response(asStringAlways)
      .send(backend)

    assert(response1.code == StatusCode.NotFound)

    val response2 = basicRequest
      .get(uri"http://localhost:8080/secure/f1")
      .auth
      .bearer("secret123")
      .response(asStringAlways)
      .send(backend)

    assert(response2.code == StatusCode.Ok)
    assert(response2.body == "f1 content")
  }

  Await.result(bindAndCheck.transformWith { r => actorSystem.terminate().transform(_ => r) }, 1.minute)
}
