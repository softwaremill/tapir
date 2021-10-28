package sttp.tapir

import sttp.capabilities.Streams
import sttp.model.Uri._
import sttp.model._
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.SchemaType.SProductField
import sttp.tapir.internal.NoStreams
import sttp.tapir.model.{ConnectionInfo, ServerRequest}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interpreter.{BodyListener, RawValue, RequestBody, ToResponseBody}

import java.nio.charset.Charset
import scala.collection.immutable
import scala.util.{Success, Try}

object TestUtil {
  def field[T, U](_name: FieldName, _schema: Schema[U]): SchemaType.SProductField[T] = SProductField[T, U](_name, _schema, _ => None)

  type Id[X] = X

  object TestRequestBody extends RequestBody[Id, NoStreams] {
    override val streams: Streams[NoStreams] = NoStreams
    override def toRaw[R](bodyType: RawBodyType[R]): Id[RawValue[R]] = ???
    override def toStream(): streams.BinaryStream = ???
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

  case class PersonsApi(logic: String => Id[Either[String, String]] = PersonsApi.defaultLogic) {
    def serverEp: ServerEndpoint[Unit, Unit, String, String, String, Any, Id] = endpoint
      .in("person")
      .in(query[String]("name"))
      .out(stringBody)
      .errorOut(stringBody)
      .serverLogic(logic)
  }

  object PersonsApi {
    val defaultLogic: String => Id[Either[String, String]] = name => (if (name == "Jacob") Right("hello") else Left(":(")).unit

    val request: String => ServerRequest = name => {
      new ServerRequest {
        override def protocol: String = ""
        override def connectionInfo: ConnectionInfo = ConnectionInfo(None, None, None)
        override def underlying: Any = ()
        override def pathSegments: List[String] = List("person")
        override def queryParameters: QueryParams = if (name == "") QueryParams.apply() else QueryParams.fromSeq(Seq(("name", name)))
        override def method: Method = Method.GET
        override def uri: Uri = uri"http://example.com/person"
        override def headers: immutable.Seq[Header] = Nil
      }
    }
  }
}
