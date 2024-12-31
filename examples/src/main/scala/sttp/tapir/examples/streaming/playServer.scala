//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.11
//> using dep com.softwaremill.sttp.tapir::tapir-play-server:1.11.11
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-cats:1.11.11
//> using dep org.playframework::play-netty-server:3.0.6
//> using dep com.softwaremill.sttp.client3::core:3.10.2

package sttp.tapir.examples.streaming

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import sttp.capabilities.pekko.PekkoStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.*
import sttp.tapir.server.play.PlayServerInterpreter

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import sttp.model.{HeaderNames, MediaType, Part, StatusCode}
import sttp.tapir.*

import scala.concurrent.{ExecutionContext, Future}
import scala.util.*
import org.apache.pekko
import pekko.stream.scaladsl.{Flow, Source}
import pekko.util.ByteString
import sttp.client3.UriContext
import sttp.tapir.server.netty.cats.NettyCatsServer
import sttp.tapir.server.netty.NettyConfig
import scala.concurrent.duration.DurationInt
import cats.effect.{IO, Resource}
import cats.effect.std.Dispatcher
import scala.concurrent.duration.FiniteDuration
import fs2.{Chunk, Stream}
import cats.effect.unsafe.implicits.global

import sttp.capabilities.fs2.Fs2Streams

given ExecutionContext = ExecutionContext.global

type ErrorInfo = String

implicit val actorSystem: ActorSystem = ActorSystem("playServer")

val givenRequestTimeout = 2.seconds
val chunkSize = 100
val beforeSendingSecondChunk: FiniteDuration = 2.second

def createStream(chunkSize: Int, beforeSendingSecondChunk: FiniteDuration): fs2.Stream[IO, Byte] = {
  val chunk = Chunk.array(Array.fill(chunkSize)('A'.toByte))
  val initialChunks = fs2.Stream.chunk(chunk)
  val delayedChunk = fs2.Stream.sleep[IO](beforeSendingSecondChunk) >> fs2.Stream.chunk(chunk)
  initialChunks ++ delayedChunk
}

val inputStream = createStream(chunkSize, beforeSendingSecondChunk)

val e = endpoint.post
    .in("chunks")
    .in(header[Long](HeaderNames.ContentLength))
    .in(streamTextBody(Fs2Streams[IO])(CodecFormat.TextPlain()))
    .out(header[Long](HeaderNames.ContentLength))
    .out(streamTextBody(Fs2Streams[IO])(CodecFormat.TextPlain()))
    .serverLogicSuccess[IO] { case (length, stream) =>
      IO((length, stream))
    }

val config =
  NettyConfig.default
    .host("0.0.0.0")
    .port(9000)
    .requestTimeout(givenRequestTimeout)


//val routes = PlayServerInterpreter().toRoutes(e)

@main def playServer(): Unit =
  import play.api.Configuration
  import play.api.Mode
  import play.core.server.ServerConfig

  import java.io.File
  import java.util.Properties

  println(s"Server is starting...")

  NettyCatsServer
    .io(config)
    .use { server =>
      for {
        binding <- server
          .addEndpoint(e)
          .start()
        result <- IO
          .blocking {
            val port = binding.port
            val host = binding.hostName
            println(s"Server started at port = ${binding.port}")
          }
          .guarantee(binding.stop())
      } yield result
    }.unsafeRunSync()

  println(s"Server started at port ???")