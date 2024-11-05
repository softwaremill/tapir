// {cat=Static content; effects=Future; server=Pekko HTTP}: Serving static files secured with a bearer token

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-files:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-pekko-http-server:1.11.8
//> using dep com.softwaremill.sttp.client3::core:3.9.8

package sttp.tapir.examples.static_content

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.server.Route
import sttp.client3.*
import sttp.model.StatusCode
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.files.*
import sttp.tapir.server.pekkohttp.PekkoHttpServerInterpreter

import java.nio.file.{Files, Path, StandardOpenOption}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

@main def staticContentSecurePekkoServer(): Unit =
  // creating test files
  val exampleDirectory: Path = Files.createTempDirectory("pekko-static-secure-example")
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
  val route: Route = PekkoHttpServerInterpreter().toRoute(secureFileEndpoints)

  val bindAndCheck = Http().newServerAt("localhost", 8080).bindFlow(route).map { binding =>
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

    binding
  }

  Await.result(bindAndCheck.flatMap(_.terminate(1.minute)), 1.minute)
