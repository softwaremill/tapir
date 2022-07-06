package sttp.tapir.server.http4s

import org.http4s.HttpRoutes
import org.http4s.server.websocket.WebSocketBuilder2
import sttp.capabilities.zio.ZioStreams
import sttp.model.sse.ServerSentEvent
import sttp.tapir.{streamTextBody, CodecFormat, StreamBodyIO}
import sttp.tapir.ztapir.{ZioServerSentEvents, ZServerRoutes}
import zio.stream._
import zio.RIO

import java.nio.charset.Charset

package object ztapir {

  type ZHttp4sServerRoutes[R] =
    ZServerRoutes[R, HttpRoutes[RIO[R, *]]]

  type ZHttp4sServerWebSocketRoutes[R] =
    ZServerRoutes[R, WebSocketBuilder2[RIO[R, *]] => HttpRoutes[RIO[R, *]]]

  val serverSentEventsBody: StreamBodyIO[Stream[Throwable, Byte], Stream[Throwable, ServerSentEvent], ZioStreams] = {
    streamTextBody(ZioStreams)(CodecFormat.TextEventStream(), Some(Charset.forName("UTF-8")))
      .map(ZioServerSentEvents.parseBytesToSSE)(ZioServerSentEvents.serialiseSSEToBytes)
  }
}
