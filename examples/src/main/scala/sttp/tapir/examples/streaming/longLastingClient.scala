//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.11
//> using dep org.apache.pekko::pekko-stream:1.1.2
//> using dep org.typelevel::cats-effect:3.5.7
//> using dep com.softwaremill.sttp.client3::core:3.10.2
//> using dep com.softwaremill.sttp.client3::pekko-http-backend:3.10.2

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

import scala.concurrent.duration.*
import scala.concurrent.duration.FiniteDuration

object longLastingClient extends IOApp:
  implicit val actorSystem: ActorSystem = ActorSystem("longLastingClient")

  private val givenLength: Long = 10000
  private val chunkSize = 100
  private val noChunks = givenLength / chunkSize

  private def makeRequest(backend: SttpBackend[Future, PekkoStreams & WebSockets]): Future[Response[Either[String, String]]] =
    val stream: Source[ByteString, Any] =
      Source.tick(1.seconds, 1.seconds, ByteString(Array.fill(chunkSize)('A').map(_.toByte)))
        .zipWithIndex
        .take(noChunks)
        .map { case (chunk, idx) =>
           println(s"Chunk ${idx + 1} sent ${java.time.LocalTime.now()}"); chunk
        }

    basicRequest
      .post(uri"http://localhost:9000/chunks")
      .header(Header(HeaderNames.ContentLength, givenLength.toString))
      .streamBody(PekkoStreams)(stream)
      .send(backend)

  override def run(args: List[String]): IO[ExitCode] =
      val backend = PekkoHttpBackend.usingActorSystem(actorSystem)
      val responseIO: IO[Response[Either[String, String]]] = IO.fromFuture(IO(makeRequest(backend)))
      responseIO.flatMap { response =>
        IO(println(response.body))
      }.as(ExitCode.Success)