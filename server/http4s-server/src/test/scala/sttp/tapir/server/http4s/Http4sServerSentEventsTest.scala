package sttp.tapir.server.http4s

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.model.sse.ServerSentEvent

import java.nio.charset.StandardCharsets

class Http4sServerSentEventsTest extends AsyncFunSuite with Matchers {

  test("serialiseSSEToBytes should successfully serialise simple Server Sent Event to ByteString") {
    val sse: fs2.Stream[IO, ServerSentEvent] = fs2.Stream(ServerSentEvent(Some("data"), Some("event"), Some("id1"), Some(10)))
    val serialised = Http4sServerSentEvents.serialiseSSEToBytes[IO](sse)
    val futureEventsBytes = serialised.compile.toList
    futureEventsBytes
      .map(sseEvents => {
        sseEvents shouldBe
          s"""data: data
             |event: event
             |id: id1
             |retry: 10
             |
             |""".stripMargin.getBytes(StandardCharsets.UTF_8).toList
      })
      .unsafeToFuture()
  }

  test("serialiseSSEToBytes should omit fields that are not set") {
    val sse = fs2.Stream(ServerSentEvent(Some("data"), None, Some("id1"), None))
    val serialised = Http4sServerSentEvents.serialiseSSEToBytes[IO](sse)
    val futureEvents = serialised.compile.toList
    futureEvents
      .map(sseEvents => {
        sseEvents shouldBe
          s"""data: data
             |id: id1
             |
             |""".stripMargin.getBytes(StandardCharsets.UTF_8).toList
      })
      .unsafeToFuture()
  }

  test("serialiseSSEToBytes should successfully serialise multiline data event") {
    val sse = fs2.Stream(
      ServerSentEvent(
        Some("""some data info 1
               |some data info 2
               |some data info 3""".stripMargin),
        None,
        None,
        None
      )
    )
    val serialised = Http4sServerSentEvents.serialiseSSEToBytes[IO](sse)
    val futureEvents = serialised.compile.toList
    futureEvents
      .map(sseEvents => {
        sseEvents shouldBe
          s"""data: some data info 1
             |data: some data info 2
             |data: some data info 3
             |
             |""".stripMargin.getBytes(StandardCharsets.UTF_8).toList
      })
      .unsafeToFuture()
  }

  test("parseBytesToSSE should successfully parse SSE bytes to SSE structure") {
    val sseBytes = fs2.Stream.iterable(
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
    val parsed = Http4sServerSentEvents.parseBytesToSSE[IO](sseBytes)
    val futureEvents = parsed.compile.toList
    futureEvents
      .map(events =>
        events shouldBe List(
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
      .unsafeToFuture()
  }

}
