package sttp.tapir.client.play

import akka.stream.scaladsl.Source
import akka.util.ByteString
import sttp.capabilities.akka.AkkaStreams
import sttp.tapir.client.tests.ClientStreamingTests

import scala.concurrent.Await
import scala.concurrent.duration._

class PlayClientStreamingTests extends PlayClientTests[AkkaStreams] with ClientStreamingTests[AkkaStreams] {

  override val streams: AkkaStreams = AkkaStreams

  override def mkStream(s: String): Source[ByteString, Any] = Source.fromIterator(() => Iterator(ByteString.fromString(s)))
  override def rmStream(s: Source[ByteString, Any]): String = {
    val str = s.runFold("")((u, t) => u + t.decodeString("UTF-8"))
    Await.result(str, 30.seconds)
  }

  streamingTests()
}
