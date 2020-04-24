package sttp.tapir.server.vertx

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset

import io.vertx.core.MultiMap
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpHeaders
import io.vertx.scala.core.http.HttpServerResponse
import io.vertx.scala.core.streams.{Pump, ReadStream}
import io.vertx.scala.ext.web.RoutingContext
import sttp.model.{Header, Part}
import sttp.tapir.internal._
import sttp.tapir.server.ServerDefaults
import sttp.tapir.server.internal.{EncodeOutputBody, EncodeOutputs, OutputValues}
import sttp.tapir.{CodecFormat, EndpointOutput, RawBodyType}

import scala.collection.JavaConverters._

object VertxOutputEncoders {

  type RoutingContextHandler = RoutingContext => Unit

  def apply[O](output: EndpointOutput[O], v: O, isError: Boolean = false): RoutingContextHandler = { rc =>
    val resp = rc.response
    try {
      val options: OutputValues[RoutingContextHandler] = OutputValues.empty
      var outputValues = encodeOutputs(output, ParamsAsAny(v), options)
      if (isError && outputValues.statusCode.isEmpty) {
        outputValues = outputValues.withStatusCode(ServerDefaults.StatusCodes.error) // FIXME: override ServerDefaults
      }
      setStatus(outputValues)(resp)
      forwardHeaders(outputValues)(resp)
      outputValues.body match {
        case Some(responseHandler)  => responseHandler(rc)
        case None                   => resp.end()
      }
    } catch {
      case e: Throwable => rc.fail(e)
    }
  }

  private def setStatus[O](outputValues: OutputValues[O])(resp: HttpServerResponse): Unit =
    outputValues.statusCode.map(_.code).foreach(resp.setStatusCode)

  private val encodeOutputs: EncodeOutputs[RoutingContextHandler] = new EncodeOutputs(new EncodeOutputBody[RoutingContextHandler] {
    override def rawValueToBody(v: Any, format: CodecFormat, bodyType: RawBodyType[_]): RoutingContextHandler =
      handleBody(bodyType.asInstanceOf[RawBodyType[Any]], formatToContentType(format/*, charset(bodyType)*/), v)(_)
    override def streamValueToBody(v: Any, format: CodecFormat, charset: Option[Charset]): RoutingContextHandler =
      handleStream(formatToContentType(format/*, charset*/), v.asInstanceOf[ReadStream[Buffer]])(_)
  })

  private def formatToContentType(format: CodecFormat/*, maybeCharset: Option[Charset]*/): String =
    format.mediaType.toString

  private def handleBody[CF <: CodecFormat, R](
    bodyType: RawBodyType[R],
    contentType: String,
    r: R,
  )(rc: RoutingContext): Unit = {
    val resp = rc.response
    if (resp.headers.get(HttpHeaders.CONTENT_TYPE.toString).isEmpty) {
      resp.putHeader(HttpHeaders.CONTENT_TYPE.toString, contentType)
    }
    bodyType match {
      case RawBodyType.StringBody(charset) => resp.end(r.toString, charset.toString)
      case RawBodyType.ByteArrayBody => resp.end(Buffer.buffer(r.asInstanceOf[Array[Byte]]))
      case RawBodyType.ByteBufferBody => resp.end(Buffer.buffer().setBytes(0, r.asInstanceOf[ByteBuffer]))
      case RawBodyType.InputStreamBody => throw new UnsupportedOperationException("Using InputStreams (i.e. blocking IOs) with Vert.x is not supported")
      case RawBodyType.FileBody => resp.sendFile(r.asInstanceOf[File].getPath)
      case m: RawBodyType.MultipartBody => handleBodyParts(m, r)(rc)
    }
    ()
  }

  private def handleBodyParts[CF <: CodecFormat, R](multipart: RawBodyType[R] with RawBodyType.MultipartBody, r: R)(rc: RoutingContext): Unit = {
    val resp = rc.response()
    resp.setChunked(true)
    resp.putHeader(HttpHeaders.CONTENT_TYPE.toString, "multipart/form-data")
    r.asInstanceOf[Seq[Part[_]]].foreach { rawPartToBodyPart(multipart, _)(rc) }
    if (!resp.ended()) {
      resp.end()
    }
  }

  private def rawPartToBodyPart[T](m: RawBodyType.MultipartBody, part: Part[T])(rc: RoutingContext): Unit = {
    val resp = rc.response()
    m.partType(part.name).foreach { partType =>
      forwardHeaders(part.headers)(resp)
      val partContentType = part.contentType.getOrElse("application/octet-stream")
      val dispositionParams = part.otherDispositionParams + (Part.NameDispositionParam -> part.name)
      val dispositionsHeader = dispositionParams.map { case (k, v) => s"""$k="$v"""" }
      resp.write(s"${HttpHeaders.CONTENT_DISPOSITION}: form-data; ${dispositionsHeader.mkString(", ")}")
      resp.write("\n")
      resp.write(s"${HttpHeaders.CONTENT_TYPE}: $partContentType")
      resp.write("\n\n")
      handleBodyPart(partType.asInstanceOf[RawBodyType[Any]], partContentType, part.body)(rc)
    }
  }

  private def handleBodyPart[CF <: CodecFormat, R](bodyType: RawBodyType[R], contentType: String, r: R)(rc: RoutingContext): Unit = {
    val resp = rc.response()
    resp.write(s"${HttpHeaders.CONTENT_TYPE}: $contentType")
    resp.write("\n")
    bodyType match {
      case RawBodyType.StringBody(charset) => resp.write(r.toString, charset.toString)
      case RawBodyType.ByteArrayBody => resp.write(Buffer.buffer(r.asInstanceOf[Array[Byte]]))
      case RawBodyType.ByteBufferBody => resp.write(Buffer.buffer().setBytes(0, r.asInstanceOf[ByteBuffer]))
      case RawBodyType.InputStreamBody => throw new UnsupportedOperationException("Using InputStreams (i.e. blocking IOs) with Vert.x is not supported")
      case RawBodyType.FileBody =>
        val file = r.asInstanceOf[File]
        resp.write(s"""${HttpHeaders.CONTENT_DISPOSITION.toString}: file; file="${file.getName}"""")
        resp.write("\n")
        resp.write(rc.vertx.fileSystem.readFileBlocking(file.getAbsolutePath))
        resp.write("\n\n")
      case m: RawBodyType.MultipartBody => handleBodyParts(m, r)(rc)
    }
    ()
  }


  private def handleStream(contentType: String, stream: ReadStream[Buffer])(rc: RoutingContext): Unit = {
    val resp = rc.response
    resp.putHeader(HttpHeaders.CONTENT_TYPE.toString, contentType)
    resp.setChunked(true)
    val pump = Pump.pump(stream, resp)
    stream.endHandler { _ =>
      pump.stop()
      ()
    }
    ()
  }

  // README: even though Vert.x supports headers as a MultiMap, it doesn't propagates multiple values in a header
  // Therefore, we have to build "multi-headers" value manually
  // But not for set-cookie. There's definitely something weird happening in the way Vert.x handles the ` headers` MultiMap
  private def forwardHeaders(outputValues: OutputValues[RoutingContextHandler])(resp: HttpServerResponse): Unit = {
    val headersMap = MultiMap.caseInsensitiveMultiMap()
    outputValues.headers.groupBy(_._1).foreach { case (name, values) =>
      if (!name.equalsIgnoreCase(HttpHeaders.SET_COOKIE.toString)) {
        headersMap.add(name, values.map(_._2).mkString(", "))
      } else {
        headersMap.add(name, values.map(_._2).asJava)
      }
    }
    if (resp.headers.contains(HttpHeaders.CONTENT_TYPE.toString)) { // already set by CodecFormat
      headersMap.remove(HttpHeaders.CONTENT_TYPE.toString)
    }
    resp.headers.addAll(io.vertx.scala.core.MultiMap(headersMap))
    ()
  }

  private def forwardHeaders(headers: Seq[Header])(resp: HttpServerResponse): Unit = {
    headers.foreach { h =>
      resp.headers().add(h.name, h.value)
    }
  }
}
