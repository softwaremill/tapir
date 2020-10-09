package sttp.tapir.server.akkahttp

import akka.http.scaladsl.server.Route
import sttp.capabilities.akka.AkkaStreams
import sttp.tapir.server.tests.ServerStreamingTests

import scala.concurrent.Future

class AkkaHttpServerStreamingTests extends AkkaHttpServerTests[AkkaStreams] with ServerStreamingTests[Future, AkkaStreams, Route] {
  override val streams: AkkaStreams = AkkaStreams
  streamingTests()
}
