package sttp.tapir.server

import sttp.capabilities.Streams
import sttp.model._
import sttp.shared.Identity
import sttp.tapir._
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter.{BodyListener, RawValue, RequestBody, ToResponseBody}

import java.nio.charset.Charset
import scala.util.{Success, Try}

object TestUtil {
  object TestRequestBody extends RequestBody[Identity, NoStreams] {
    override val streams: Streams[NoStreams] = NoStreams
    override def toRaw[R](serverRequest: ServerRequest, bodyType: RawBodyType[R], maxBytes: Option[Long]): Identity[RawValue[R]] = ???
    override def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): streams.BinaryStream = ???
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

  implicit val unitBodyListener: BodyListener[Identity, Unit] = new BodyListener[Identity, Unit] {
    override def onComplete(body: Unit)(cb: Try[Unit] => Identity[Unit]): Unit = {
      cb(Success(()))
      ()
    }
  }

  implicit val stringBodyListener: BodyListener[Identity, String] = new BodyListener[Identity, String] {
    override def onComplete(body: String)(cb: Try[Unit] => Identity[Unit]): String = {
      cb(Success(()))
      body
    }
  }
}
