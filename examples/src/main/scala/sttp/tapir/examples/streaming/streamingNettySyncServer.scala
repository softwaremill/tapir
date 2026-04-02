// {cat=Streaming; effects=Direct; server=Netty}: Stream request and response bodies as Ox Flows

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.13.14
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-sync:1.13.14
//> using dep com.softwaremill.sttp.client4::core:4.0.15

package sttp.tapir.examples.streaming

import ox.*
import ox.flow.Flow
import sttp.client4.*
import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.model.HeaderNames
import sttp.tapir.*
import sttp.tapir.server.netty.sync.{NettySyncServer, OxStreams}

import java.nio.charset.StandardCharsets
import scala.concurrent.duration.*

@main def streamingNettySyncServer(): Unit =
  // corresponds to: GET /receive
  // We need to provide both the schema of the value (for documentation), as well as the format (media type) of the
  // body. Here, the schema is a `string` (set by `streamTextBody`) and the media type is `text/plain`.
  val receiveEndpoint =
    endpoint.get
      .in("receive")
      .out(header[Long](HeaderNames.ContentLength))
      .out(streamTextBody(OxStreams)(CodecFormat.TextPlain(), Some(StandardCharsets.UTF_8)))

  val size = 100L
  val receiveServerEndpoint = receiveEndpoint.handleSuccess: _ =>
    val stream = Flow
      .repeat(List('a', 'b', 'c', 'd'))
      .mapConcat(identity)
      .throttle(1, 100.millis)
      .take(size.toInt)
      .map(c => Chunk(c.toByte))
    (size, stream)

  // corresponds to: POST /send
  // Accepts a streaming text body and processes it character by character as data arrives.
  val sendEndpoint =
    endpoint.post
      .in("send")
      .in(streamTextBody(OxStreams)(CodecFormat.TextPlain(), Some(StandardCharsets.UTF_8)))
      .out(stringBody)

  val sendServerEndpoint = sendEndpoint.handleSuccess: stream =>
    print("[server-side] ")
    val count = stream
      .mapConcat(chunk => chunk.toList)
      .tap(byte => print(byte.toChar))
      .runFold(0)((acc, _) => acc + 1)
    println()
    s"Received $count bytes"

  supervised:
    val binding = NettySyncServer()
      .addEndpoints(List(receiveServerEndpoint, sendServerEndpoint))
      .start()
    releaseAfterScope(binding.stop())

    println(s"Server started at http://localhost:8080")

    val backend: SyncBackend = HttpClientSyncBackend()

    println("Test streaming response: GET /receive")
    // Reading the response as an InputStream so we can process it character by character as data arrives
    val receiveResult = basicRequest
      .response(asInputStreamAlways { is =>
        val sb = StringBuilder()
        Iterator.continually(is.read()).takeWhile(_ != -1).foreach { byte =>
          val c = byte.toChar
          print(c)
          sb.append(c)
        }
        println()
        sb.toString
      })
      .get(uri"http://localhost:8080/receive")
      .send(backend)
      .body
    assert(receiveResult == "abcd" * 25)

    println("\nTest streaming request: POST /send")
    val sendResult: String =
      basicRequest.response(asStringAlways).body("Hello, streaming!").post(uri"http://localhost:8080/send").send(backend).body
    println("[client-side] Got result: " + sendResult)
    assert(sendResult == "Received 17 bytes")
