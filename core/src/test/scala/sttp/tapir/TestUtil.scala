package sttp.tapir

import sttp.capabilities
import sttp.capabilities.Streams
import sttp.model.HasHeaders
import sttp.monad.MonadError
import sttp.tapir.SchemaType.SProductField
import sttp.tapir.internal.NoStreams
import sttp.tapir.server.interpreter.{BodyListener, RequestBody, ToResponseBody}

import java.nio.charset.Charset
import scala.util.{Success, Try}

object TestUtil {
  def field[T, U](_name: FieldName, _schema: Schema[U]): SchemaType.SProductField[T] = SProductField[T, U](_name, _schema, _ => None)

  type Id[X] = X

  object TestRequestBody extends RequestBody[Id, Nothing] {
    override val streams: Streams[Nothing] = NoStreams
    override def toRaw[R](bodyType: RawBodyType[R]): Id[R] = ???
    override def toStream(): streams.BinaryStream = ???
  }

  object UnitToResponseBody extends ToResponseBody[Unit, Nothing] {
    override val streams: Streams[Nothing] = NoStreams
    override def fromRawValue[R](v: R, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[R]): Unit = ()
    override def fromStreamValue(
        v: streams.BinaryStream,
        headers: HasHeaders,
        format: CodecFormat,
        charset: Option[Charset]
    ): Unit = ???
    override def fromWebSocketPipe[REQ, RESP](
        pipe: streams.Pipe[REQ, RESP],
        o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, Nothing]
    ): Unit = ???
  }

  object StringToResponseBody extends ToResponseBody[String, Nothing] {
    override val streams: capabilities.Streams[Nothing] = NoStreams
    override def fromRawValue[R](v: R, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[R]): String =
      v.asInstanceOf[String]
    override def fromStreamValue(v: streams.BinaryStream, headers: HasHeaders, format: CodecFormat, charset: Option[Charset]): String = ""
    override def fromWebSocketPipe[REQ, RESP](
        pipe: streams.Pipe[REQ, RESP],
        o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, Nothing]
    ): String = ""
  }

  implicit val idMonadError: MonadError[Id] = new MonadError[Id] {
    override def unit[T](t: T): Id[T] = t
    override def map[T, T2](fa: Id[T])(f: T => T2): Id[T2] = f(fa)
    override def flatMap[T, T2](fa: Id[T])(f: T => Id[T2]): Id[T2] = f(fa)
    override def error[T](t: Throwable): Id[T] = throw t
    override protected def handleWrappedError[T](rt: Id[T])(h: PartialFunction[Throwable, Id[T]]): Id[T] = rt
    override def ensure[T](f: Id[T], e: => Id[Unit]): Id[T] = try f
    finally e
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
