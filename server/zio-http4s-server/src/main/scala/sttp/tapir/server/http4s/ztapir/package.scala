package sttp.tapir.server.http4s

import sttp.capabilities.zio.ZioStreams
import sttp.model.sse.ServerSentEvent
import sttp.tapir.ztapir.ZioServerSentEvents
import sttp.tapir.{CodecFormat, StreamBodyIO, streamTextBody}
import zio.stream._

import java.nio.charset.Charset

package object ztapir {
  val serverSentEventsBody: StreamBodyIO[Stream[Throwable, Byte], Stream[Throwable, ServerSentEvent], ZioStreams] = {
    streamTextBody(ZioStreams)(CodecFormat.TextEventStream(), Some(Charset.forName("UTF-8")))
      .map(ZioServerSentEvents.parseBytesToSSE)(ZioServerSentEvents.serialiseSSEToBytes)
  }
}
