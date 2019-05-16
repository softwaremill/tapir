package tapir.server.finatra
import com.twitter.finagle.http.Response
import com.twitter.io.Buf
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

object OutputToFinatraResponse {
  def apply[O, E](output: EndpointOutput[O], v: Any, r: Option[Response] = None): Response = {
    val vs = ParamsToSeq(v)
    val response = r.getOrElse(Response())

    // There should only be one content-type header, so if we're
    // adding a content-type, use 'set' rather than 'add'.
    def setOrAddHeader(name: String, value: String): Unit = {
      if (name.toLowerCase() == "content-type") {
        response.headerMap.set(name, value)
      } else {
        response.headerMap.add(name, value)
      }
    }

    output.asVectorOfSingleOutputs.zipWithIndex.foreach {
      case (EndpointIO.Body(codec, _), i) =>
        codec.encode(vs(i)).map(rawValueToBuf(codec.meta, _)) match {
          case Some((content, contentType)) =>
            response.content = content
            response.contentType = contentType
          case None =>
        }

      case (EndpointIO.StreamBodyWrapper(StreamingEndpointIO.Body(_, mediaType, _)), i) =>
        response.contentType = mediaType.mediaType
        ???

      case (EndpointIO.Header(name, codec, _), i) =>
        codec
          .encode(vs(i))
          .foreach((headerValue: String) => setOrAddHeader(name, headerValue))

      case (EndpointIO.Headers(_), i) =>
        vs(i).asInstanceOf[Seq[(String, String)]].foreach {
          case (name, value) => setOrAddHeader(name, value)
        }

      case (EndpointIO.Mapped(wrapped, _, g, _), i) =>
        apply(wrapped, g(vs(i)), Some(response))

      case (EndpointOutput.StatusCode(), i) =>
        response.statusCode = vs(i).asInstanceOf[Int]

      case (EndpointOutput.StatusFrom(io, default, _, when), i) =>
        val v = vs(i)
        val sc = when.find(_._1.matches(v)).map(_._2).getOrElse(default)
        apply(io, v)
        response.statusCode = sc

      case (EndpointOutput.Mapped(wrapped, _, g, _), i) =>
        apply(wrapped, g(vs(i)), Some(response))
    }

    response
  }

  private def rawValueToBuf[M <: MediaType, R](codecMeta: CodecMeta[M, R], r: R): (Buf, String) = {
    val ct: String = codecMeta.mediaType.mediaType

    codecMeta.rawValueType match {
      case StringValueType(charset) =>
        Buf.ByteArray.Owned(r.toString.getBytes(charset)) -> ct
      case ByteArrayValueType  => Buf.ByteArray.Owned(r) -> ct
      case ByteBufferValueType => Buf.ByteBuffer.Owned(r) -> ct
      case InputStreamValueType =>
        ???
      case FileValueType =>
        ???
      case mvt: MultipartValueType =>
        ???
    }
  }

}
