// {cat=Server Sent Events; effects=Direct; server=Netty}: Describe and implement an endpoint which emits SSE

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.45
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-sync:1.11.45

package sttp.tapir.examples.sse

import ox.*
import ox.flow.Flow
import sttp.model.sse.ServerSentEvent
import sttp.tapir.*
import sttp.tapir.server.netty.sync.{NettySyncServer, OxStreams, serverSentEventsBody}

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.concurrent.duration.*

// The endpoint description
val sseEndpoint = endpoint.get.in("sse").out(serverSentEventsBody)

// The flow representing the server-sent events
val dateTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
val sseFlow = Flow
  .tick(1.second) // emit a new event every second
  .map(_ => s"Event at ${LocalDateTime.now().format(dateTimeFormat)}")
  .take(10)
  .map(event => ServerSentEvent(data = Some(event)))

@main def sseNettySyncServer(): Unit =
  val sseServerEndpoint = sseEndpoint.handleSuccess(_ => sseFlow)

  NettySyncServer()
    .host("0.0.0.0")
    .port(8080)
    .addEndpoints(List(sseServerEndpoint))
    .startAndWait()
