package tapir.server.finatra
import com.twitter.finagle.http.{Response, Status, Version}
import com.twitter.io.Buf

case class FinatraResponse(
    status: Status,
    content: FinatraContent = FinatraContentBuf(Buf.Empty),
    contentType: String = "text/plain",
    headerMap: Seq[(String, String)] = Seq.empty
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
