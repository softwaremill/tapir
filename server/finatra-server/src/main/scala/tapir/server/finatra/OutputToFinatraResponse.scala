package tapir.server.finatra
import com.twitter.finagle.http.{Response, Status, Version}
import com.twitter.io.{Buf, BufReader, InputStreamReader, Reader}
import tapir.internal.{ParamsToSeq, _}
import tapir.{
  ByteArrayValueType,
  ByteBufferValueType,
  CodecMeta,
  EndpointIO,
  EndpointOutput,
  FileValueType,
  InputStreamValueType,
  MediaType,
  MultipartValueType,
  StreamingEndpointIO,
  StringValueType
}

sealed trait FinatraContent
case class FinatraContentBuf(buf: Buf) extends FinatraContent
case class FinatraContentReader(reader: Reader[Buf]) extends FinatraContent

case class FinatraResponse(
    status: Status,
    content: FinatraContent = FinatraContentBuf(Buf.Empty),
    contentType: String = "text/plain",
    headerMap: Seq[(String, String)] = Seq.empty,
) {
  def toResponse: Response = {
    val responseWithContent = content match {
      case FinatraContentBuf(buf) =>
        val response = Response(Version.Http11, status)
        response.content = buf
        response
      case FinatraContentReader(reader) =>
        Response(Version.Http11, status, reader)
    }
    responseWithContent.contentType = contentType
    headerMap.foreach { case (name, value) => responseWithContent.headerMap.add(name, value) }

    responseWithContent
  }

  // There should only be one content-type header, so if we're
  // adding a content-type, use 'set' rather than 'add'.
  def setOrAddHeader(name: String, value: String): FinatraResponse = {
    if (name.toLowerCase() == "content-type") {
      this.copy(contentType = value)
    } else {
      this.copy(headerMap = this.headerMap :+ (name -> value))
    }
  }
}

object OutputToFinatraResponse {
  def apply[O, E](output: EndpointOutput[O],
                  v: Any,
                  startingResponse: Option[FinatraResponse] = None,
                  defaultStatus: Status = Status.Ok): FinatraResponse = {
    val vs = ParamsToSeq(v)

    output.asVectorOfSingleOutputs.zipWithIndex.foldLeft(startingResponse.getOrElse(FinatraResponse(defaultStatus))) {
      case (finatraResponse, input) =>
        input match {
          case (EndpointIO.Body(codec, _), i) =>
            codec.encode(vs(i)).map(rawValueToFinatraContent(codec.meta, _)) match {
              case Some((content, contentType)) =>
                finatraResponse.copy(content = content, contentType = contentType)
              case None =>
                ???
            }

          case (EndpointIO.StreamBodyWrapper(StreamingEndpointIO.Body(_, mediaType, _)), i) =>
            finatraResponse.copy(contentType = mediaType.mediaType)
            ???

          case (EndpointIO.Header(name, codec, _), i) =>
            codec
              .encode(vs(i))
              .foldLeft(finatraResponse) {
                case (fr, value) => fr.setOrAddHeader(name, value)
              }

          case (EndpointIO.Headers(_), i) =>
            vs(i).asInstanceOf[Seq[(String, String)]].foldLeft(finatraResponse) {
              case (fr, (name, value)) => fr.setOrAddHeader(name, value)
            }

          case (EndpointIO.Mapped(wrapped, _, g, _), i) =>
            apply(wrapped, g(vs(i)), Some(finatraResponse))

          case (EndpointOutput.StatusCode(), i) =>
            finatraResponse.copy(status = Status(vs(i).asInstanceOf[Int]))

          case (EndpointOutput.StatusFrom(io, default, _, when), i) =>
            val v = vs(i)
            val sc = when.find(_._1.matches(v)).map(_._2).getOrElse(default)
            apply(io, v).copy(status = Status(sc))

          case (EndpointOutput.Mapped(wrapped, _, g, _), i) =>
            apply(wrapped, g(vs(i)), Some(finatraResponse))
        }
    }

  }

  private def rawValueToFinatraContent[M <: MediaType, R](codecMeta: CodecMeta[M, R], r: R): (FinatraContent, String) = {
    val ct: String = codecMeta.mediaType.mediaType

    codecMeta.rawValueType match {
      case StringValueType(charset) =>
        FinatraContentBuf(Buf.ByteArray.Owned(r.toString.getBytes(charset))) -> ct
      case ByteArrayValueType  => FinatraContentBuf(Buf.ByteArray.Owned(r)) -> ct
      case ByteBufferValueType => FinatraContentBuf(Buf.ByteBuffer.Owned(r)) -> ct
      case InputStreamValueType =>
        FinatraContentReader(InputStreamReader(r)) -> ct
      case FileValueType =>
        ???
      case mvt: MultipartValueType =>
        ???
    }
  }

}
