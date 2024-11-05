// {cat=Streaming; effects=Future; server=Pekko HTTP}: Stream response as a Pekko stream

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-pekko-http-server:1.11.8
//> using dep com.softwaremill.sttp.client3::core:3.9.8

package sttp.tapir.examples.streaming

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import sttp.capabilities.pekko.PekkoStreams
import sttp.client3.*
import sttp.shared.Identity
import sttp.tapir.server.pekkohttp.PekkoHttpServerInterpreter
import sttp.tapir.*

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.*

@main def streamingPekkoServer(): Unit =
  implicit val actorSystem: ActorSystem = ActorSystem()
  import actorSystem.dispatcher

  // The endpoint: corresponds to GET /receive.
  // We need to provide both the schema of the value (for documentation), as well as the format (media type) of the
  // body. Here, the schema is a `string` and the media type is `text/plain`.
  val streamingEndpoint: PublicEndpoint[Unit, Unit, Source[ByteString, Any], PekkoStreams] =
    endpoint.get.in("receive").out(streamTextBody(PekkoStreams)(CodecFormat.TextPlain()))

  // converting an endpoint to a route (providing server-side logic); extension method comes from imported packages
  val testStream: Source[ByteString, Any] = Source.repeat("Hello!").take(10).map(s => ByteString(s))
  val streamingRoute: Route = PekkoHttpServerInterpreter().toRoute(streamingEndpoint.serverLogicSuccess(_ => Future.successful(testStream)))

  // starting the server
  val bindAndCheck = Http().newServerAt("localhost", 8080).bindFlow(streamingRoute).map { binding =>
    // testing
    val backend: SttpBackend[Identity, Any] = HttpClientSyncBackend()
    val result: String = basicRequest.response(asStringAlways).get(uri"http://localhost:8080/receive").send(backend).body
    println("Got result: " + result)

    assert(result == "Hello!" * 10)

    binding
  }

  Await.result(bindAndCheck.flatMap(_.terminate(1.minute)), 1.minute)
