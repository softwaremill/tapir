// {cat=Streaming; effects=ZIO; server=ZIO HTTP}: Stream response as a ZIO stream

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-zio-http-server:1.11.8
//> using dep com.softwaremill.sttp.client3::core:3.9.8

package sttp.tapir.examples.streaming

import sttp.capabilities.zio.ZioStreams
import sttp.model.HeaderNames
import sttp.tapir.{CodecFormat, PublicEndpoint}
import sttp.tapir.ztapir.*
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import zio.http.{Response => ZioHttpResponse, Routes, Server}
import zio.{ExitCode, Schedule, URIO, ZIO, ZIOAppDefault, ZLayer}
import zio.stream.*

import java.nio.charset.StandardCharsets
import java.time.Duration

object StreamingZioHttpServer extends ZIOAppDefault:
  // corresponds to: GET /receive?name=...
  // We need to provide both the schema of the value (for documentation), as well as the format (media type) of the
  // body. Here, the schema is a `string` (set by `streamTextBody`) and the media type is `text/plain`.
  val streamingEndpoint: PublicEndpoint[Unit, Unit, (Long, Stream[Throwable, Byte]), ZioStreams] =
    endpoint.get
      .in("receive")
      .out(header[Long](HeaderNames.ContentLength))
      .out(streamTextBody(ZioStreams)(CodecFormat.TextPlain(), Some(StandardCharsets.UTF_8)))

  // converting an endpoint to a route (providing server-side logic)
  val streamingServerEndpoint: ZServerEndpoint[Any, ZioStreams] = streamingEndpoint.zServerLogic { _ =>
    val size = 100L

    val stream = ZStream
      .tick(Duration.ofMillis(100))
      .zipWith(ZStream[Char]('a', 'b', 'c', 'd').repeat(Schedule.forever))((_, c) => c)
      .take(size)
      .map(_.toByte)

    ZIO.succeed((size, stream))
  }

  val routes: Routes[Any, ZioHttpResponse] = ZioHttpInterpreter().toHttp(streamingServerEndpoint)

  // Test using: curl http://localhost:8080/receive
  override def run: URIO[Any, ExitCode] =
    Server
      .serve(routes)
      .provide(
        ZLayer.succeed(Server.Config.default.port(8080)),
        Server.live
      )
      .exitCode
