package sttp.tapir.ztapir

import sttp.model.sse.ServerSentEvent
import zio.Chunk
import zio.test._
import zio.test.Assertion._
import zio.stream._

import java.nio.charset.StandardCharsets

object ZioServerSentEventsTest extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] =
    suite("ZioServerSentEvents tests")(
      test("serialiseSSEToBytes should successfully serialise simple Server Sent Event to ByteString") {
        val sse: Stream[Nothing, ServerSentEvent] = ZStream(ServerSentEvent(Some("data"), Some("event"), Some("id1"), Some(10)))
        val serialised = ZioServerSentEvents.serialiseSSEToBytes(sse)
        serialised.runCollect.map { sseEvents =>
          assert(sseEvents.toList)(equalTo(s"""data: data
             |event: event
             |id: id1
             |retry: 10
             |
             |""".stripMargin.getBytes(StandardCharsets.UTF_8).toList))
        }
      },
      test("serialiseSSEToBytes should omit fields that are not set") {
        val sse = ZStream(ServerSentEvent(Some("data"), None, Some("id1"), None))
        val serialised = ZioServerSentEvents.serialiseSSEToBytes(sse)
        serialised.runCollect.map { sseEvents =>
          assert(sseEvents.toList)(equalTo(s"""data: data
             |id: id1
             |
             |""".stripMargin.getBytes(StandardCharsets.UTF_8).toList))
        }
      },
      test("serialiseSSEToBytes should successfully serialise multiline data event") {
        val sse = ZStream(
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
             |""".stripMargin.getBytes(StandardCharsets.UTF_8).toList))
        }
      },
      test("parseBytesToSSE should successfully parse SSE bytes to SSE structure") {
        val sseBytes = ZStream.fromChunk(
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
          |""".stripMargin.getBytes(StandardCharsets.UTF_8)
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
