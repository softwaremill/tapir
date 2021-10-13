package sttp.tapir.examples

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.{Route, RouteConcatenation}
import sttp.client3._
import sttp.model.{ContentRangeUnits, Header, HeaderNames, StatusCode}
import sttp.tapir._
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter

import java.nio.file.{Files, Path, StandardOpenOption}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

object StaticContentStremingAkkaServer extends App {
  private val parent: Path = Files.createTempDirectory("akka-static-example")

  parent.resolve("d1/d2").toFile.mkdirs()

  Files.write(parent.resolve("f1"), "f1 content".getBytes, StandardOpenOption.CREATE_NEW)

  private val exampleFile = parent.resolve("f1").toFile
  private val exampleFilePath = exampleFile.getAbsolutePath

  private val fileEndpoints = fileHeadAndGetServerEndpoints[Future]("range-example")(exampleFilePath)
  private val headRoute: Route = AkkaHttpServerInterpreter().toRoute(fileEndpoints._1)
  private val fileRoute: Route = AkkaHttpServerInterpreter().toRoute(fileEndpoints._2)

  // starting the server
  private implicit val actorSystem: ActorSystem = ActorSystem()
  import actorSystem.dispatcher

  private val route: Route = RouteConcatenation.concat(headRoute, fileRoute)
  private val bindAndCheck: Future[Unit] = Http().newServerAt("localhost", 8080).bindFlow(route).map { _ =>
    // testing
    val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()
    val headResponse = basicRequest
      .head(uri"http://localhost:8080/range-example")
      .response(asStringAlways)
      .send(backend)

    assert(headResponse.code == StatusCode.Ok)
    assert(headResponse.headers.contains(Header(HeaderNames.AcceptRanges, ContentRangeUnits.Bytes)))
    assert(headResponse.headers contains Header(HeaderNames.ContentLength, exampleFile.length.toString))

    val getResponse = basicRequest
      .headers(Header(HeaderNames.Range, "bytes=3-6"))
      .get(uri"http://localhost:8080/range-example")
      .response(asStringAlways)
      .send(backend)

    assert(getResponse.body == "cont")
    assert(getResponse.code == StatusCode.PartialContent)
    assert(getResponse.body.length == 4)
    assert(getResponse.headers contains Header(HeaderNames.ContentRange, "bytes 3-6/10"))

  }

  Await.result(bindAndCheck.transformWith { r => actorSystem.terminate().transform(_ => r) }, 1.minute)
}
