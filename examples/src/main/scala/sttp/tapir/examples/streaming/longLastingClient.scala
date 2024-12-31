//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.11
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-cats:1.11.11
//> using dep org.apache.pekko::pekko-stream:1.1.2
//> using dep org.typelevel::cats-effect:3.5.7
//> using dep com.softwaremill.sttp.client3::core:3.10.2
//> using dep com.softwaremill.sttp.client3::pekko-http-backend:3.10.1
//> using dep com.softwaremill.sttp.client3::fs2:3.10.2

package sttp.tapir.examples.streaming

import cats.effect.{ExitCode, IO, IOApp, Resource}
import sttp.capabilities.WebSockets
import sttp.client3.pekkohttp.PekkoHttpBackend
import sttp.client3.{Response, SttpBackend, UriContext, basicRequest}

import scala.concurrent.Future
import sttp.model.{Header, HeaderNames, Method, QueryParams}
import sttp.tapir.*
import org.apache.pekko
import org.apache.pekko.actor.ActorSystem
import sttp.capabilities.pekko.PekkoStreams
import pekko.stream.scaladsl.{Flow, Source}
import pekko.util.ByteString
import cats.effect.*
import cats.syntax.all.*
import sttp.client3.httpclient.fs2.HttpClientFs2Backend
import scala.concurrent.duration.*
import scala.concurrent.duration.FiniteDuration
import sttp.capabilities.fs2.Fs2Streams
import fs2.{Chunk, Stream}

object longLastingClient extends IOApp:
  private def makeRequest(backend: SttpBackend[IO, Fs2Streams[IO] & WebSockets]): IO[Response[Either[String, String]]] =
    def createStream(chunkSize: Int, beforeSendingSecondChunk: FiniteDuration): Stream[IO, Byte] = {
      val chunk = Chunk.array(Array.fill(chunkSize)('A'.toByte))
      val initialChunks = Stream.chunk(chunk)
      val delayedChunk = Stream.sleep[IO](beforeSendingSecondChunk) >> Stream.chunk(chunk)
      initialChunks ++ delayedChunk
    }
    val stream = createStream(100, 2.seconds)
  
    basicRequest
      .post(uri"http://localhost:9000/chunks")
      .header(Header(HeaderNames.ContentLength, "10000"))
      .streamBody(Fs2Streams[IO])(stream)
      .send(backend)
  
  override def run(args: List[String]): IO[ExitCode] =
    HttpClientFs2Backend.resource[IO]().use { backend =>
      makeRequest(backend).flatMap { response =>
        IO(println(response.body))
      }
    }.as(ExitCode.Success)