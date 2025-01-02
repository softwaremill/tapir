//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.11
//> using dep com.softwaremill.sttp.tapir::tapir-play-server:1.11.11
//> using dep org.playframework::play-netty-server:3.0.6
//> using dep com.softwaremill.sttp.client3::core:3.10.2
//> using dep org.slf4j:slf4j-simple:2.0.16

package sttp.tapir.examples.streaming

import play.core.server.*
import play.api.routing.Router.Routes
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
import pekko.stream.scaladsl.{Flow, Source, Sink}
import pekko.util.ByteString
import sttp.tapir.server.play.PlayServerOptions

given ExecutionContext = ExecutionContext.global

type ErrorInfo = String

implicit val actorSystem: ActorSystem = ActorSystem("playServer")

def handleErrors[T](f: Future[T]): Future[Either[ErrorInfo, T]] =
  f.transform {
    case Success(v) => Success(Right(v))
    case Failure(e) =>
      println(s"Exception when running endpoint logic: $e")
      Success(Left(e.getMessage))
  }

def logic(s: (Long, Source[ByteString, Any])): Future[String] = {
  val (length, stream) = s
  println(s"Transmitting $length bytes...")
  val result = stream
    .runFold(List.empty[ByteString])((acc, byteS) => acc :+ byteS)
    .map(_.reduce(_ ++ _).decodeString("UTF-8"))
  result.onComplete {
    case Failure(ex) =>
      println(s"Stream failed with exception: $ex" )
    case Success(s) =>
      println(s"Stream finished: ${s.length}/$length transmitted")
  }
  result
}

val e = endpoint.post
    .in("chunks")
    .in(header[Long](HeaderNames.ContentLength))
    .in(streamTextBody(PekkoStreams)(CodecFormat.TextPlain()))
    .out(stringBody)
    .errorOut(plainBody[ErrorInfo])
    .serverLogic(logic.andThen(handleErrors))


val routes = PlayServerInterpreter(PlayServerOptions.customiseInterceptors().serverLog(PlayServerOptions.defaultServerLog.logWhenReceived(true)
  .logAllDecodeFailures(true)).options).toRoutes(e)

@main def playServer(): Unit =
  import play.api.Configuration
  import play.api.Mode
  import play.core.server.ServerConfig


  import java.io.File
  import java.util.Properties

  val customConfig = Configuration(
    "play.server.http.idleTimeout" -> "75 seconds",
    "play.server.https.idleTimeout" -> "75 seconds",
    "play.server.https.wantClientAuth" -> false,
    "play.server.https.needClientAuth" -> false,
    "play.server.netty.server-header" -> null,
    "play.server.netty.shutdownQuietPeriod" -> "2 seconds",
    "play.server.netty.maxInitialLineLength" -> "4096",
    "play.server.netty.maxChunkSize" -> "8192",
    "play.server.netty.eventLoopThreads" -> "0",
    "play.server.netty.transport" -> "jdk",
    "play.server.max-header-size" -> "8k",
    "play.server.waitBeforeTermination" -> "0",
    "play.server.deferBodyParsing" -> false,
    "play.server.websocket.frame.maxLength" -> "64k",
    "play.server.websocket.periodic-keep-alive-mode" -> "ping",
    "play.server.websocket.periodic-keep-alive-max-idle" -> "infinite",
    "play.server.max-content-length" -> "infinite",
    "play.server.netty.log.wire" -> true,
    "play.server.netty.option.child.SO_KEEPALIVE" -> false,
    "play.server.pekko.requestTimeout" -> "5 seconds",
  )
  val serverConfig = ServerConfig(
    rootDir = new File("."),
    port = Some(9000),
    sslPort = None,
    address = "0.0.0.0",
    mode = Mode.Dev,
    properties = System.getProperties,
    configuration = customConfig
  )

  NettyServer.fromRouterWithComponents(serverConfig) { components => routes }