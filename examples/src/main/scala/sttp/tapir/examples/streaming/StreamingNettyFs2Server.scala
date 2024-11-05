// {cat=Streaming; effects=cats-effect; server=Netty}: Stream response as an fs2 stream

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-cats:1.11.8
//> using dep com.softwaremill.sttp.client3::core:3.9.8

package sttp.tapir.examples.streaming

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits.*
import fs2.{Chunk, Stream}
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.*
import sttp.model.HeaderNames
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.cats.{NettyCatsServer, NettyCatsServerBinding}

import java.nio.charset.StandardCharsets
import scala.concurrent.duration.*

object StreamingNettyFs2Server extends IOApp:
  // corresponds to: GET /receive?name=...
  // We need to provide both the schema of the value (for documentation), as well as the format (media type) of the
  // body. Here, the schema is a `string` (set by `streamTextBody`) and the media type is `text/plain`.
  val streamingEndpoint: PublicEndpoint[Unit, Unit, (Long, Stream[IO, Byte]), Fs2Streams[IO]] =
    endpoint.get
      .in("receive")
      .out(header[Long](HeaderNames.ContentLength))
      .out(streamTextBody(Fs2Streams[IO])(CodecFormat.TextPlain(), Some(StandardCharsets.UTF_8)))

  val serverEndpoint: ServerEndpoint[Fs2Streams[IO], IO] = streamingEndpoint
    .serverLogicSuccess { _ =>
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
    }

  private val declaredPort = 9090
  private val declaredHost = "localhost"

  override def run(args: List[String]): IO[ExitCode] =
    // starting the server
    NettyCatsServer
      .io()
      .use { server =>
        val startServer: IO[NettyCatsServerBinding[IO]] = server
          .port(declaredPort)
          .host(declaredHost)
          .addEndpoint(serverEndpoint)
          .start()

        startServer
          .map { binding =>

            println(s"Server started at port = ${binding.port}")

            val backend: SttpBackend[Identity, Any] = HttpClientSyncBackend()
            val result: String =
              basicRequest.response(asStringAlways).get(uri"http://$declaredHost:$declaredPort/receive").send(backend).body
            println("Got result: " + result)

            assert(result == "abcd" * 25)
          }
          .as(ExitCode.Success)
      }
