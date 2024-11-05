// {cat=WebSocket; effects=Direct; server=Netty}: Describe and implement a WebSocket endpoint

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-sync:1.11.8

package sttp.tapir.examples.websocket

import ox.*
import ox.channels.*
import ox.flow.Flow
import sttp.capabilities.WebSockets
import sttp.tapir.*
import sttp.tapir.server.netty.sync.OxStreams
import sttp.tapir.server.netty.sync.OxStreams.Pipe
import sttp.tapir.server.netty.sync.NettySyncServer

import scala.concurrent.duration.*

object WebSocketNettySyncServer:
  // Web socket endpoint
  val wsEndpoint =
    endpoint.get
      .in("ws")
      .out(webSocketBody[String, CodecFormat.TextPlain, String, CodecFormat.TextPlain](OxStreams))

  // Your processor transforming a stream of requests into a stream of responses
  val wsPipe: Pipe[String, String] = requestStream => requestStream.map(_.toUpperCase)

  // Alternative logic (not used here): requests and responses can be treated separately, for example to emit frames
  // to the client from another source.
  val wsPipe2: Pipe[String, String] = in =>
    // emit periodic responses
    val responseFlow: Flow[String] = Flow.tick(1.second).map(_ => System.currentTimeMillis()).map(_.toString)

    // ignore whatever is sent by the client, but complete the stream once the client closes
    in.drain().merge(responseFlow, propagateDoneLeft = true)

  // The WebSocket endpoint, builds the pipeline in serverLogicSuccess
  val wsServerEndpoint = wsEndpoint.handleSuccess(_ => wsPipe)

  // A regular /GET endpoint
  val helloWorldEndpoint =
    endpoint.get.in("hello").in(query[String]("name")).out(stringBody)

  val helloWorldServerEndpoint = helloWorldEndpoint
    .handleSuccess(name => s"Hello, $name!")

  def main(args: Array[String]): Unit =
    NettySyncServer()
      .host("0.0.0.0")
      .port(8080)
      .addEndpoints(List(wsServerEndpoint, helloWorldServerEndpoint))
      .startAndWait()
