package sttp.tapir.server

import akka.http.scaladsl.model.ResponseEntity
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.{Flow, Source}
import akka.util.ByteString
import sttp.capabilities.akka.AkkaStreams
import sttp.capabilities.WebSockets
import sttp.model.sse.ServerSentEvent
import sttp.tapir.{streamTextBody, CodecFormat, StreamBodyIO}

import java.nio.charset.Charset
import scala.concurrent.Future

package object akkahttp {

  type AkkaServerRoutes = ServerRoutes[Future, Route]
  type AkkaResponseBody = Either[Flow[Message, Message, Any], ResponseEntity]

  val serverSentEventsBody: StreamBodyIO[Source[ByteString, Any], Source[ServerSentEvent, Any], AkkaStreams] =
    streamTextBody(AkkaStreams)(CodecFormat.TextEventStream(), Some(Charset.forName("UTF-8")))
      .map(AkkaServerSentEvents.parseBytesToSSE)(AkkaServerSentEvents.serialiseSSEToBytes)
}
