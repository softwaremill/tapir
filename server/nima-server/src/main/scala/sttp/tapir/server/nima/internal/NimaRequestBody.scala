package sttp.tapir.server.nima.internal

import io.helidon.webserver.http.{ServerRequest => JavaNimaServerRequest}
import sttp.capabilities
import sttp.shared.Identity
import sttp.tapir.{FileRange, InputStreamRange, RawBodyType, TapirFile}
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter.{RawValue, RequestBody}

import java.nio.ByteBuffer
import java.nio.file.{Files, StandardCopyOption}

private[nima] class NimaRequestBody(createFile: ServerRequest => TapirFile) extends RequestBody[Identity, NoStreams] {
  override val streams: capabilities.Streams[NoStreams] = NoStreams

  override def toRaw[RAW](serverRequest: ServerRequest, bodyType: RawBodyType[RAW], maxBytes: Option[Long]): RawValue[RAW] = {
    def asInputStream = nimaRequest(serverRequest).content().inputStream()
    def asByteArray = asInputStream.readAllBytes()

    bodyType match {
      case RawBodyType.StringBody(charset)  => RawValue(new String(asByteArray, charset))
      case RawBodyType.ByteArrayBody        => RawValue(asByteArray)
      case RawBodyType.ByteBufferBody       => RawValue(ByteBuffer.wrap(asByteArray))
      case RawBodyType.InputStreamBody      => RawValue(asInputStream)
      case RawBodyType.InputStreamRangeBody => RawValue(InputStreamRange(() => asInputStream))
      case RawBodyType.FileBody =>
        val file = createFile(serverRequest)
        Files.copy(asInputStream, file.toPath, StandardCopyOption.REPLACE_EXISTING)
        RawValue(FileRange(file), Seq(FileRange(file)))
      case _: RawBodyType.MultipartBody => throw new UnsupportedOperationException("Multipart request body not supported")
    }
  }

  override def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): streams.BinaryStream =
    throw new UnsupportedOperationException()

  private def nimaRequest(serverRequest: ServerRequest): JavaNimaServerRequest =
    serverRequest.underlying.asInstanceOf[JavaNimaServerRequest]
}
