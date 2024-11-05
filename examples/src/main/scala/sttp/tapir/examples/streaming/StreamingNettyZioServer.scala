// {cat=Streaming; effects=ZIO; server=Netty}: Stream response as a ZIO stream

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-zio:1.11.8
//> using dep com.softwaremill.sttp.client3::core:3.9.8

package sttp.tapir.examples.streaming

import sttp.capabilities.zio.ZioStreams
import sttp.client3.*
import sttp.model.HeaderNames
import sttp.shared.Identity
import sttp.tapir.{CodecFormat, PublicEndpoint}
import sttp.tapir.server.netty.zio.NettyZioServer
import sttp.tapir.ztapir.*
import zio.interop.catz.*
import zio.*
import zio.stream.*

import java.nio.charset.StandardCharsets

object StreamingNettyZioServer extends ZIOAppDefault:
  // corresponds to: GET /receive?name=...
  // We need to provide both the schema of the value (for documentation), as well as the format (media type) of the
  // body. Here, the schema is a `string` (set by `streamTextBody`) and the media type is `text/plain`.
  val streamingEndpoint: PublicEndpoint[Unit, Unit, (Long, ZStream[Any, Throwable, Byte]), ZioStreams] =
    endpoint.get
      .in("receive")
      .out(header[Long](HeaderNames.ContentLength))
      .out(streamTextBody(ZioStreams)(CodecFormat.TextPlain(), Some(StandardCharsets.UTF_8)))

  val serverEndpoint: ZServerEndpoint[Any, ZioStreams] = streamingEndpoint
    .zServerLogic { _ =>
      val size = 100L
      val stream = ZStream
        .tick(100.millis)
        .zipWith(ZStream[Char]('a', 'b', 'c', 'd').repeat(Schedule.forever))((_, c) => c)
        .take(size)
        .map(_.toByte)

      ZIO.succeed((size, stream))
    }

  private val declaredPort = 9090
  private val declaredHost = "localhost"

  override def run: URIO[Any, ExitCode] =
    (for {
      binding <- NettyZioServer()
        .port(declaredPort)
        .host(declaredHost)
        .addEndpoint(serverEndpoint)
        .start()
      _ = {
        println(s"Server started at port = ${binding.port}")

        val backend: SttpBackend[Identity, Any] = HttpClientSyncBackend()
        val result: String =
          basicRequest.response(asStringAlways).get(uri"http://$declaredHost:$declaredPort/receive").send(backend).body
        println("Got result: " + result)

        assert(result == "abcd" * 25)
      }
      _ <- binding.stop()
    } yield ()).exitCode
