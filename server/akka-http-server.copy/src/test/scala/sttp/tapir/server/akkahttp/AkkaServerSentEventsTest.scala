package sttp.tapir.server.akkahttp

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.model.sse.ServerSentEvent

class AkkaServerSentEventsTest extends AsyncFunSuite with Matchers {

  implicit val materializer: Materializer = Materializer(ActorSystem("AkkaHttpServerInterpreterTest"))

  test("serialiseSSEToBytes should successfully serialise simple Server Sent Event to ByteString") {
    val sse = Source.single(ServerSentEvent(Some("data"), Some("event"), Some("id1"), Some(10)))
    val serialised = AkkaServerSentEvents.serialiseSSEToBytes(sse)
    val futureEvents = serialised.runFold(List.empty[ByteString])((acc, event) => acc :+ event)
    futureEvents.map(sseEvents => {
      sseEvents shouldBe List(
        ByteString(
          s"""data: data
             |event: event
             |id: id1
             |retry: 10
             |
             |""".stripMargin
        )
      )
    })
  }

  test("serialiseSSEToBytes should omit fields that are not set") {
    val sse = Source.single(ServerSentEvent(Some("data"), None, Some("id1"), None))
    val serialised = AkkaServerSentEvents.serialiseSSEToBytes(sse)
    val futureEvents = serialised.runFold(List.empty[ByteString])((acc, event) => acc :+ event)
    futureEvents.map(sseEvents => {
      sseEvents shouldBe List(
        ByteString(
          s"""data: data
             |id: id1
             |
             |""".stripMargin
        )
      )
    })
  }

  test("serialiseSSEToBytes should successfully serialise multiline data event") {
    val sse = Source.single(
      ServerSentEvent(
        Some("""some data info 1
        |some data info 2
        |some data info 3""".stripMargin),
        None,
        None,
        None
      )
    )
    val serialised = AkkaServerSentEvents.serialiseSSEToBytes(sse)
    val futureEvents = serialised.runFold(List.empty[ByteString])((acc, event) => acc :+ event)
    futureEvents.map(sseEvents => {
      sseEvents shouldBe List(
        ByteString(
          s"""data: some data info 1
             |data: some data info 2
             |data: some data info 3
             |
             |""".stripMargin
        )
      )
    })
  }

  test("parseBytesToSSE should successfully parse SSE bytes to SSE structure") {
    val sseBytes = Source.single(
      ByteString(
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
        |""".stripMargin
      )
    )
    val parsed = AkkaServerSentEvents.parseBytesToSSE(sseBytes)
    val futureEvents = parsed.runFold(List.empty[ServerSentEvent])((acc, event) => acc :+ event)
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
    )
  }

}
