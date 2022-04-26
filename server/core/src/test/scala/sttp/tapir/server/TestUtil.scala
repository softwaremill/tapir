package sttp.tapir.server

import sttp.capabilities.Streams
import sttp.model._
import sttp.tapir.TestUtil.Id
import sttp.tapir._
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter.{BodyListener, RawValue, RequestBody, ToResponseBody}

import java.nio.charset.Charset
import scala.util.{Success, Try}

object TestUtil {
  object TestRequestBody extends RequestBody[Id, NoStreams] {
    override val streams: Streams[NoStreams] = NoStreams
    override def toRaw[R](serverRequest: ServerRequest, bodyType: RawBodyType[R]): Id[RawValue[R]] = ???
    override def toStream(serverRequest: ServerRequest): streams.BinaryStream = ???
  }

  object UnitToResponseBody extends ToResponseBody[Unit, NoStreams] {
    override val streams: Streams[NoStreams] = NoStreams
    override def fromRawValue[R](v: R, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[R]): Unit = ()
    override def fromStreamValue(
        v: streams.BinaryStream,
        headers: HasHeaders,
        format: CodecFormat,
        charset: Option[Charset]
    ): Unit = ???
    override def fromWebSocketPipe[REQ, RESP](
        pipe: streams.Pipe[REQ, RESP],
        o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, NoStreams]
    ): Unit = ???
  }

  object StringToResponseBody extends ToResponseBody[String, NoStreams] {
    override val streams: Streams[NoStreams] = NoStreams
    override def fromRawValue[R](v: R, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[R]): String =
      v.asInstanceOf[String]
    override def fromStreamValue(v: streams.BinaryStream, headers: HasHeaders, format: CodecFormat, charset: Option[Charset]): String = ""
    override def fromWebSocketPipe[REQ, RESP](
        pipe: streams.Pipe[REQ, RESP],
        o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, NoStreams]
    ): String = ""
  }

  implicit val unitBodyListener: BodyListener[Id, Unit] = new BodyListener[Id, Unit] {
    override def onComplete(body: Unit)(cb: Try[Unit] => Id[Unit]): Unit = {
      cb(Success(()))
      ()
    }
  }

  implicit val stringBodyListener: BodyListener[Id, String] = new BodyListener[Id, String] {
    override def onComplete(body: String)(cb: Try[Unit] => Id[Unit]): String = {
      cb(Success(()))
      body
    }
  }
}
