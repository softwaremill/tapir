// {cat=Streaming; effects=cats-effect; server=http4s}: Stream response as an fs2 stream

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-http4s-server:1.11.8
//> using dep com.softwaremill.sttp.client3::core:3.9.8
//> using dep org.http4s::http4s-blaze-server:0.23.16

package sttp.tapir.examples.streaming

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits.*
import fs2.{Chunk, Stream}
import org.http4s.HttpRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.*
import sttp.model.HeaderNames
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.server.http4s.Http4sServerInterpreter

import java.nio.charset.StandardCharsets
import scala.concurrent.duration.*

// https://github.com/softwaremill/tapir/issues/367
object StreamingHttp4sFs2Server extends IOApp:
  // corresponds to: GET /receive?name=...
  // We need to provide both the schema of the value (for documentation), as well as the format (media type) of the
  // body. Here, the schema is a `string` (set by `streamTextBody`) and the media type is `text/plain`.
  val streamingEndpoint: PublicEndpoint[Unit, Unit, (Long, Stream[IO, Byte]), Fs2Streams[IO]] =
    endpoint.get
      .in("receive")
      .out(header[Long](HeaderNames.ContentLength))
      .out(streamTextBody(Fs2Streams[IO])(CodecFormat.TextPlain(), Some(StandardCharsets.UTF_8)))

  // converting an endpoint to a route (providing server-side logic)
  val streamingRoutes: HttpRoutes[IO] =
    Http4sServerInterpreter[IO]().toRoutes(streamingEndpoint.serverLogicSuccess { _ =>
      val size = 100L
      Stream
        .emit(List[Char]('a', 'b', 'c', 'd'))
        .repeat
        .flatMap(list => Stream.chunk(Chunk.from(list)))
        .metered[IO](100.millis)
        .take(size)
        .covary[IO]
        .map(_.toByte)
        .pure[IO]
        .map(s => (size, s))
    })

  override def run(args: List[String]): IO[ExitCode] =
    // starting the server
    BlazeServerBuilder[IO]
      .bindHttp(8080, "localhost")
      .withHttpApp(Router("/" -> streamingRoutes).orNotFound)
      .resource
      .use { _ =>
        IO {
          val backend: SttpBackend[Identity, Any] = HttpClientSyncBackend()
          val result: String = basicRequest.response(asStringAlways).get(uri"http://localhost:8080/receive").send(backend).body
          println("Got result: " + result)

          assert(result == "abcd" * 25)
        }
      }
      .as(ExitCode.Success)
