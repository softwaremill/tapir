package sttp.tapir.server.jdkhttp
package internal

import com.sun.net.httpserver.HttpExchange
import sttp.capabilities
import sttp.model.Part
import sttp.shared.Identity
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter.{RawValue, RequestBody}
import sttp.tapir.server.jdkhttp.internal.ParsedMultiPart.parseMultipartBody
import sttp.tapir.server.model.InvalidMultipartBodyException
import sttp.tapir.{FileRange, InputStreamRange, RawBodyType, RawPart, TapirFile}

import java.io._
import java.nio.ByteBuffer
import java.nio.file.{Files, StandardCopyOption}

private[jdkhttp] class JdkHttpRequestBody(createFile: ServerRequest => TapirFile, multipartFileThresholdBytes: Long)
    extends RequestBody[Identity, NoStreams] {
  override val streams: capabilities.Streams[NoStreams] = NoStreams

  override def toRaw[RAW](serverRequest: ServerRequest, bodyType: RawBodyType[RAW], maxBytes: Option[Long]): RawValue[RAW] = {
    val request = jdkHttpRequest(serverRequest)
    toRaw(serverRequest, bodyType, request.getRequestBody, maxBytes)
  }

  private def toRaw[RAW](
      serverRequest: ServerRequest,
      bodyType: RawBodyType[RAW],
      body: InputStream,
      maxBytes: Option[Long]
  ): RawValue[RAW] = {
    def asInputStream: InputStream = maxBytes.map(limit => new FailingLimitedInputStream(body, limit)).getOrElse(body)
    def asByteArray: Array[Byte] = asInputStream.readAllBytes()

    bodyType match {
      case RawBodyType.InputStreamRangeBody => RawValue(InputStreamRange(() => asInputStream))
      case RawBodyType.StringBody(charset)  => RawValue(new String(asByteArray, charset))
      case RawBodyType.ByteArrayBody        => RawValue(asByteArray)
      case RawBodyType.ByteBufferBody       => RawValue(ByteBuffer.wrap(asByteArray))
      case RawBodyType.InputStreamBody      => RawValue(asInputStream)
      case RawBodyType.FileBody             =>
        val file = createFile(serverRequest)
        Files.copy(asInputStream, file.toPath, StandardCopyOption.REPLACE_EXISTING)
        RawValue(FileRange(file), Seq(FileRange(file)))
      case m: RawBodyType.MultipartBody => RawValue.fromParts(multiPartRequestToRawBody(serverRequest, asInputStream, m))
    }
  }

  private val boundaryPrefix = "boundary="
  private def extractBoundary(request: HttpExchange): String = {
    Option(request.getRequestHeaders.getFirst("Content-Type"))
      .flatMap(
        _.split(";")
          .find(_.trim().startsWith(boundaryPrefix))
          .map(line => {
            val boundary = line.trim().substring(boundaryPrefix.length)
            if (boundary.length > 70)
              throw new IllegalArgumentException("Multipart boundary must be no longer than 70 characters.")
            s"--$boundary"
          })
      )
      .getOrElse(throw new IllegalArgumentException("Unable to extract multipart boundary from multipart request"))
  }

  private def multiPartRequestToRawBody(request: ServerRequest, requestBody: InputStream, m: RawBodyType.MultipartBody): Seq[RawPart] = {
    val httpExchange = jdkHttpRequest(request)
    val boundary = extractBoundary(httpExchange)

    try {
      parseMultipartBody(requestBody, boundary, multipartFileThresholdBytes).flatMap(parsedPart =>
        parsedPart.getName.flatMap(name =>
          m.partType(name)
            .map(partType => {
              val bodyRawValue = toRaw(request, partType, parsedPart.getBody, maxBytes = None)
              Part(
                name,
                bodyRawValue.value,
                otherDispositionParams = parsedPart.getDispositionParams - "name",
                headers = parsedPart.fileItemHeaders
              )
            })
        )
      )
    } catch {
      case e: Exception if e.getMessage().contains("Parsing multipart failed") => throw InvalidMultipartBodyException(e)
    }
  }

  override def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): streams.BinaryStream =
    throw new UnsupportedOperationException(
      "Streaming is not supported"
    )

  private def jdkHttpRequest(serverRequest: ServerRequest): HttpExchange =
    serverRequest.underlying.asInstanceOf[HttpExchange]
}
