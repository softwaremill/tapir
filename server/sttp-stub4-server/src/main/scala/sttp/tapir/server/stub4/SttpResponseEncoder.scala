package sttp.tapir.server.stub4

import sttp.client4._
import sttp.model.{ContentTypeRange, HasHeaders, Header, Headers, Method, RequestMetadata, StatusCode, StatusText, Uri}
import sttp.tapir.internal.ParamsAsAny
import sttp.tapir.server.interpreter.{EncodeOutputs, OutputValues, ToResponseBody}
import sttp.tapir.server.stub4.internal.SttpFileConversions
import sttp.tapir.{CodecFormat, EndpointOutput, FileRange, InputStreamRange, RangeValue, RawBodyType, WebSocketBodyOutput}

import java.io.{ByteArrayInputStream, InputStream}
import java.nio.charset.Charset
import scala.annotation.tailrec
import scala.collection.immutable.Seq

private[stub4] object SttpResponseEncoder {
  def apply(output: EndpointOutput[_], responseValue: Any, statusCode: StatusCode): Response[Any] = {
    val outputValues: OutputValues[Any] =
      new EncodeOutputs[Any, AnyStreams](toResponseBody, Seq(ContentTypeRange.AnyRange))
        .apply(output, ParamsAsAny(responseValue), OutputValues.empty)

    Response(
      outputValues.body.map(_.apply(Headers(outputValues.headers))).getOrElse(()),
      outputValues.statusCode.getOrElse(statusCode),
      StatusText.default(outputValues.statusCode.getOrElse(statusCode)).getOrElse(""),
      outputValues.headers,
      Nil,
      new RequestMetadata {
        override def method: Method = Method.GET
        override def uri: Uri = uri"http://example.com"
        override def headers: Seq[Header] = Nil
      }
    )
  }

  val toResponseBody: ToResponseBody[Any, AnyStreams] = new ToResponseBody[Any, AnyStreams] {
    override val streams: AnyStreams = AnyStreams
    override def fromRawValue[RAW](v: RAW, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[RAW]): Any =
      v match {
        // the sttp stub backend serves bodies from plain values only (`SttpFile`, `InputStream`, `String`,
        // `Array[Byte]`); tapir's range-carrying wrappers have to be unwrapped, or they are rejected outright.
        // The range is applied here, so that a stubbed partial response carries the same bytes a real server would send
        case FileRange(file, range)      => SttpFileConversions.toSttpFile(file, range)
        case InputStreamRange(is, range) => rangedInputStream(is, range)
        case other                       => other
      }
    override def fromStreamValue(v: streams.BinaryStream, headers: HasHeaders, format: CodecFormat, charset: Option[Charset]): Any = v
    override def fromWebSocketPipe[REQ, RESP](
        pipe: streams.Pipe[REQ, RESP],
        o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, AnyStreams]
    ): Any = pipe // impossible
  }

  /** Reads the given range of the stream into memory; the whole stream is returned as-is when there's no range. Which bytes are selected
    * mirrors the server interpreters (armeria, http4s, play, nima): the stream is skipped by `range.start` and limited to
    * `range.contentLength`. Note that this differs from the file path, which uses `startAndEnd` - hence a range without a start is served
    * from the head of the stream, but from the tail of a file. That asymmetry comes from `InputStreamRange` itself, and is kept here so
    * that stubbed responses carry the same bytes as the ones a real server sends.
    */
  private def rangedInputStream(inputStream: () => InputStream, range: Option[RangeValue]): InputStream =
    // whether the body is partial is decided by the same bounds as for files, so that a range without any bounds
    // yields the whole body on both paths; only which bytes are then selected follows `InputStreamRange`
    range.filter(_.startAndEnd.isDefined) match {
      case Some(rangeValue) =>
        require(rangeValue.contentLength <= Int.MaxValue, s"Range of ${rangeValue.contentLength} bytes is too large to be stubbed")
        val bytes = new Array[Byte](rangeValue.contentLength.toInt)
        val is = InputStreamRange(inputStream, range).inputStreamFromRangeStart()

        @tailrec
        def readFully(offset: Int): Int = {
          val read = if (offset < bytes.length) is.read(bytes, offset, bytes.length - offset) else -1
          if (read == -1) offset else readFully(offset + read)
        }

        val read =
          try readFully(0)
          finally is.close()
        new ByteArrayInputStream(bytes, 0, read)
      case None => inputStream()
    }
}
