package sttp.tapir.client.play

import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import sttp.capabilities.pekko.PekkoStreams
import sttp.tapir.client.tests.ClientStreamingTests

import scala.concurrent.Await
import scala.concurrent.duration._

class PlayClientStreamingTests extends PlayClientTests[PekkoStreams] with ClientStreamingTests[PekkoStreams] {

  override val streams: PekkoStreams = PekkoStreams

  override def mkStream(s: String): Source[ByteString, Any] = Source.fromIterator(() => Iterator(ByteString.fromString(s)))
  override def rmStream(s: Source[ByteString, Any]): String = {
    val str = s.runFold("")((u, t) => u + t.decodeString("UTF-8"))
    Await.result(str, 30.seconds)
  }

  streamingTests()
}
