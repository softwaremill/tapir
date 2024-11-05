// {cat=Static content; effects=Future; server=Pekko HTTP}: Serving static files from a directory, with range requests

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-files:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-pekko-http-server:1.11.8
//> using dep com.softwaremill.sttp.client3::core:3.9.8

package sttp.tapir.examples.static_content

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.server.Route
import sttp.client3.*
import sttp.model.{ContentRangeUnits, Header, HeaderNames, StatusCode}
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.files.*
import sttp.tapir.server.pekkohttp.PekkoHttpServerInterpreter

import java.nio.file.{Files, Path, StandardOpenOption}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

@main def staticContentFromFilesPekkoServer(): Unit =
  implicit val actorSystem: ActorSystem = ActorSystem()
  import actorSystem.dispatcher

  val content = "f1 content"
  val exampleDirectory: Path = Files.createTempDirectory("pekko-static-example")
  Files.write(exampleDirectory.resolve("f1"), content.getBytes, StandardOpenOption.CREATE_NEW)

  val fileEndpoints = staticFilesServerEndpoints[Future]("range-example")(exampleDirectory.toFile.getAbsolutePath)
  val route: Route = PekkoHttpServerInterpreter().toRoute(fileEndpoints)

  // starting the server
  val bindAndCheck = Http().newServerAt("localhost", 8080).bindFlow(route).map { binding =>
    // testing
    val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()
    val headResponse = basicRequest
      .head(uri"http://localhost:8080/range-example/f1")
      .response(asStringAlways)
      .send(backend)

    assert(headResponse.code == StatusCode.Ok)
    assert(headResponse.headers.contains(Header(HeaderNames.AcceptRanges, ContentRangeUnits.Bytes)))
    assert(headResponse.headers.contains(Header(HeaderNames.ContentLength, content.length.toString)))

    val getResponse = basicRequest
      .headers(Header(HeaderNames.Range, "bytes=3-6"))
      .get(uri"http://localhost:8080/range-example/f1")
      .response(asStringAlways)
      .send(backend)

    assert(getResponse.body == "cont")
    assert(getResponse.code == StatusCode.PartialContent)
    assert(getResponse.body.length == 4)
    assert(getResponse.headers.contains(Header(HeaderNames.ContentRange, "bytes 3-6/10")))

    binding
  }

  val _ = Await.result(bindAndCheck.flatMap(_.terminate(1.minute)), 1.minute)
