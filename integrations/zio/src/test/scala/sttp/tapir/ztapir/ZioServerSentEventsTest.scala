package sttp.tapir.ztapir

import sttp.model.sse.ServerSentEvent
import zio.Chunk
import zio.test.{DefaultRunnableSpec, ZSpec}
import zio.test._
import zio.test.Assertion._
import zio.stream._

import java.nio.charset.Charset

object ZioServerSentEventsTest extends DefaultRunnableSpec {
  def spec: ZSpec[TestEnvironment, Any] =
    suite("ZioServerSentEvents tests")(
      testM("serialiseSSEToBytes should successfully serialise simple Server Sent Event to ByteString") {
        val sse: Stream[Nothing, ServerSentEvent] = Stream(ServerSentEvent(Some("data"), Some("event"), Some("id1"), Some(10)))
        val serialised = ZioServerSentEvents.serialiseSSEToBytes(sse)
        serialised.runCollect.map { sseEvents =>
          assert(sseEvents.toList)(equalTo(s"""data: data
             |event: event
             |id: id1
             |retry: 10
             |
             |""".stripMargin.getBytes(Charset.forName("UTF-8")).toList))
        }
      },
      testM("serialiseSSEToBytes should omit fields that are not set") {
        val sse = Stream(ServerSentEvent(Some("data"), None, Some("id1"), None))
        val serialised = ZioServerSentEvents.serialiseSSEToBytes(sse)
        serialised.runCollect.map { sseEvents =>
          assert(sseEvents.toList)(equalTo(s"""data: data
             |id: id1
             |
             |""".stripMargin.getBytes(Charset.forName("UTF-8")).toList))
        }
      },
      testM("serialiseSSEToBytes should successfully serialise multiline data event") {
        val sse = Stream(
          ServerSentEvent(
            Some("""some data info 1
               |some data info 2
               |some data info 3""".stripMargin),
            None,
            None,
            None
          )
        )
        val serialised = ZioServerSentEvents.serialiseSSEToBytes(sse)
        serialised.runCollect.map { sseEvents =>
          assert(sseEvents.toList)(equalTo(s"""data: some data info 1
             |data: some data info 2
             |data: some data info 3
             |
             |""".stripMargin.getBytes(Charset.forName("UTF-8")).toList))
        }
      },
      testM("parseBytesToSSE should successfully parse SSE bytes to SSE structure") {
        val sseBytes = Stream.fromChunk(
          Chunk.fromArray(
            """data: event1 data
          |event: event1
          |id: id1
          |retry: 5
          |
          |
          |data: event2 data1
          |data: event2 data2
          |data: event2 data3
          |id: id2
          |
          |""".stripMargin.getBytes(Charset.forName("UTF-8"))
          )
        )
        val parsed = ZioServerSentEvents.parseBytesToSSE(sseBytes)
        parsed.runCollect.map { events =>
          assert(events.toList)(
            equalTo(
              List(
                ServerSentEvent(Some("event1 data"), Some("event1"), Some("id1"), Some(5)),
                ServerSentEvent(
                  Some("""event2 data1
                |event2 data2
                |event2 data3""".stripMargin),
                  None,
                  Some("id2"),
                  None
                )
              )
            )
          )
        }
      }
    )
}
