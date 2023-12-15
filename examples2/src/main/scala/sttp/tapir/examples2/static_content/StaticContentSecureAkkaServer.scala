package sttp.tapir.examples2.static_content

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import sttp.client3._
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.files._
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter

import java.nio.file.{Files, Path, StandardOpenOption}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

object StaticContentSecureAkkaServer extends App {
  // creating test files
  val exampleDirectory: Path = Files.createTempDirectory("akka-static-secure-example")
  Files.write(exampleDirectory.resolve("f1"), "f1 content".getBytes, StandardOpenOption.CREATE_NEW)

  implicit val actorSystem: ActorSystem = ActorSystem()
  import actorSystem.dispatcher

  // defining the endpoints
  val secureFileEndpoints = staticFilesServerEndpoints[Future]("secure")(exampleDirectory.toFile.getAbsolutePath)
    .map(_.prependSecurityPure(auth.bearer[String](), statusCode(StatusCode.Forbidden)) { token =>
      // Right means success, Left - an error, here mapped to a constant status code
      if (token.startsWith("secret")) Right(()) else Left(())
    })

  // starting the server
  val route: Route = AkkaHttpServerInterpreter().toRoute(secureFileEndpoints)

  val bindAndCheck: Future[Unit] = Http().newServerAt("localhost", 8080).bindFlow(route).map { _ =>
    // testing
    val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()
    val response1 = basicRequest
      .get(uri"http://localhost:8080/secure/f1")
      .auth
      .bearer("hacker")
      .response(asStringAlways)
      .send(backend)

    assert(response1.code == StatusCode.Forbidden)

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
