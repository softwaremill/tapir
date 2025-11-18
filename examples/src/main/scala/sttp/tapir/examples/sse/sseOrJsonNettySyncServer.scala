// {cat=Server Sent Events; effects=Direct; server=Netty}: Describe and implement an endpoint which emits SSE

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.12.3
//> using dep com.softwaremill.sttp.tapir::tapir-jsoniter-scala:1.12.3
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-sync:1.12.3
//> using dep ch.qos.logback:logback-classic:1.5.8
//> using dep com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-macros:2.30.15

package sttp.tapir.examples.sse

import ox.flow.Flow
import sttp.model.sse.ServerSentEvent
import sttp.tapir.*
import sttp.tapir.json.jsoniter.*
import sttp.tapir.server.netty.sync.{NettySyncServer, serverSentEventsBody}

import scala.concurrent.duration.*

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*

case class CompletionResponse(text: String) derives ConfiguredJsonValueCodec, Schema

// the endpoint's successful response body can be either SSE, or JSON
val sseOrJsonEndpoint = infallibleEndpoint.get
  .in("chat" / "completions")
  .in(query[Boolean]("stream"))
  .out(
    oneOf(
      // see the note: https://tapir.softwaremill.com/en/latest/endpoint/oneof.html#oneof-and-non-blocking-streaming
      oneOfVariantValueMatcher(serverSentEventsBody.toEndpointIO.map(Left(_))(_.value)) { case Left(_) => true },
      oneOfVariantValueMatcher(jsonBody[CompletionResponse].map(Right(_))(_.value)) { case Right(_) => true }
    )
  )

// the full type of the expected body of `sseOrJsonEndpoint` is
// Either[Nothing, Either[Flow[ServerSentEvent], CompletionResponse]],
// but we're using `.handleSuccess` to only handle the successful case
val sseOrJsonServerEndpoint =
  sseOrJsonEndpoint.handleSuccess: stream =>
    if stream then
      Left(
        Flow
          .tick(1.second) // emit a new event every second
          .map(_ => s"Event")
          .take(5)
          .map(event => ServerSentEvent(data = Some(event)))
      )
    else Right(CompletionResponse("Hello, world!"))

@main def sseOrJsonNettySyncServer(): Unit =
  NettySyncServer()
    .host("0.0.0.0")
    .port(8080)
    .addEndpoints(List(sseOrJsonServerEndpoint))
    .startAndWait()
