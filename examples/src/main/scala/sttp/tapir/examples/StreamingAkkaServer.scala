package sttp.tapir.examples

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Source
import akka.util.ByteString
import sttp.capabilities.akka.AkkaStreams
import sttp.client3._
import sttp.tapir._
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object StreamingAkkaServer extends App {
  // The endpoint: corresponds to GET /receive.
  // We need to provide both the schema of the value (for documentation), as well as the format (media type) of the
  // body. Here, the schema is a `string` and the media type is `text/plain`.
  val streamingEndpoint: Endpoint[Unit, Unit, Source[ByteString, Any], AkkaStreams] =
    endpoint.get.in("receive").out(streamTextBody(AkkaStreams)(CodecFormat.TextPlain()))

  // converting an endpoint to a route (providing server-side logic); extension method comes from imported packages
  val testStream: Source[ByteString, Any] = Source.repeat("Hello!").take(10).map(s => ByteString(s))
  val streamingRoute: Route = AkkaHttpServerInterpreter().toRoute(streamingEndpoint)(_ => Future.successful(Right(testStream)))

  // starting the server
  implicit val actorSystem: ActorSystem = ActorSystem()
  import actorSystem.dispatcher

  val bindAndCheck = Http().newServerAt("localhost", 8080).bindFlow(streamingRoute).map { _ =>
    // testing
    val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()
    val result: String = basicRequest.response(asStringAlways).get(uri"http://localhost:8080/receive").send(backend).body
    println("Got result: " + result)

    assert(result == "Hello!" * 10)
  }

  Await.result(bindAndCheck.transformWith { r => actorSystem.terminate().transform(_ => r) }, 1.minute)
}
