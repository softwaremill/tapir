package sttp.tapir.examples

import java.nio.charset.StandardCharsets
import cats.effect._
import cats.syntax.all._
import org.http4s.HttpRoutes
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.syntax.kleisli._
import sttp.client3._
import sttp.tapir._
import sttp.tapir.server.http4s.Http4sServerInterpreter
import fs2._
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.HeaderNames

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

// https://github.com/softwaremill/tapir/issues/367
object StreamingHttp4sFs2Server extends App {
  // corresponds to: GET /receive?name=...
  // We need to provide both the schema of the value (for documentation), as well as the format (media type) of the
  // body. Here, the schema is a `string` and the media type is `text/plain`.
  val streamingEndpoint = endpoint.get
    .in("receive")
    .out(header[Long](HeaderNames.ContentLength))
    .out(streamTextBody(Fs2Streams[IO])(CodecFormat.TextPlain(), Some(StandardCharsets.UTF_8)))

  // mandatory implicits
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit val contextShift: ContextShift[IO] = IO.contextShift(ec)
  implicit val timer: Timer[IO] = IO.timer(ec)
  implicit val concurrent: Concurrent[IO] = IO.ioConcurrentEffect

  // converting an endpoint to a route (providing server-side logic); extension method comes from imported packages
  val streamingRoutes: HttpRoutes[IO] = Http4sServerInterpreter().toRoutes(streamingEndpoint) { _ =>
    val size = 100L
    Stream
      .emit(List[Char]('a', 'b', 'c', 'd'))
      .repeat
      .flatMap(list => Stream.chunk(Chunk.seq(list)))
      .metered[IO](100.millis)
      .take(size)
      .covary[IO]
      .map(_.toByte)
      .pure[IO]
      .map(s => Right((size, s)))
  }

  // starting the server
  BlazeServerBuilder[IO](ec)
    .bindHttp(8080, "localhost")
    .withHttpApp(Router("/" -> streamingRoutes).orNotFound)
    .resource
    .use { _ =>
      IO {
        val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()
        val result: String = basicRequest.response(asStringAlways).get(uri"http://localhost:8080/receive").send(backend).body
        println("Got result: " + result)

        assert(result == "abcd" * 25)
      }
    }
    .unsafeRunSync()
}
