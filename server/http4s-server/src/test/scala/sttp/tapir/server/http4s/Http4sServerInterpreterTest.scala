package sttp.tapir.server.http4s

import cats.effect.IO
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.sse.ServerSentEvent

import java.nio.charset.Charset

class Http4sServerInterpreterTest extends AnyFunSuite with Matchers {

  test("serialiseSSEToBytes should successfully serialise simple Server Sent Event to ByteString") {
    val sse: fs2.Stream[IO, ServerSentEvent] = fs2.Stream(ServerSentEvent(Some("data"), Some("event"), Some("id1"), Some(10)))
    val serialised = Http4sServerInterpreter.serialiseSSEToBytes(Fs2Streams[IO])(sse)
    val futureEventsBytes = serialised.compile.toList
    futureEventsBytes.map(sseEvents => {
      sseEvents shouldBe
        s"""data: data
             |event: event
             |id: id1
             |retry: 10
             |
             |""".stripMargin.getBytes(Charset.forName("UTF-8")).toList
    }).unsafeRunSync()
  }

  test("serialiseSSEToBytes should omit fields that are not set") {
    val sse = fs2.Stream(ServerSentEvent(Some("data"), None, Some("id1"), None))
    val serialised = Http4sServerInterpreter.serialiseSSEToBytes(Fs2Streams[IO])(sse)
    val futureEvents = serialised.compile.toList
    futureEvents.map(sseEvents => {
      sseEvents shouldBe
          s"""data: data
             |id: id1
             |
             |""".stripMargin.getBytes(Charset.forName("UTF-8")).toList
    }).unsafeRunSync()
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
    val serialised = Http4sServerInterpreter.serialiseSSEToBytes(Fs2Streams[IO])(sse)
    val futureEvents = serialised.compile.toList
    futureEvents.map(sseEvents => {
      sseEvents shouldBe
          s"""data: some data info 1
             |data: some data info 2
             |data: some data info 3
             |
             |""".stripMargin.getBytes(Charset.forName("UTF-8")).toList
    }).unsafeRunSync()
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
          |""".stripMargin.getBytes(Charset.forName("UTF-8"))
    )
    val parsed = Http4sServerInterpreter.parseBytesToSSE(Fs2Streams[IO])(sseBytes)
    val futureEvents = parsed.compile.toList
    futureEvents.map(events =>
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
    ).unsafeRunSync()
  }

}
