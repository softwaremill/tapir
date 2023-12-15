package sttp.tapir.examples.static_content

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.server.Route
import sttp.client3.*
import sttp.model.{ContentRangeUnits, Header, HeaderNames, StatusCode}
import sttp.tapir.*
import sttp.tapir.files.*
import sttp.tapir.server.pekkohttp.PekkoHttpServerInterpreter

import java.nio.file.{Files, Path, StandardOpenOption}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

object StaticContentFromFilesPekkoServer extends App {
  implicit val actorSystem: ActorSystem = ActorSystem()
  import actorSystem.dispatcher

  val content = "f1 content"
  val exampleDirectory: Path = Files.createTempDirectory("pekko-static-example")
  Files.write(exampleDirectory.resolve("f1"), content.getBytes, StandardOpenOption.CREATE_NEW)

  val fileEndpoints = staticFilesServerEndpoints[Future]("range-example")(exampleDirectory.toFile.getAbsolutePath)
  val route: Route = PekkoHttpServerInterpreter().toRoute(fileEndpoints)

  // starting the server
  val bindAndCheck: Future[Unit] = Http().newServerAt("localhost", 8080).bindFlow(route).map { _ =>
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

  }

  Await.result(bindAndCheck.transformWith { r => actorSystem.terminate().transform(_ => r) }, 1.minute)
}
