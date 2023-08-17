package sttp.tapir.server.ziohttp

import sttp.capabilities
import sttp.capabilities.zio.ZioStreams
import sttp.model.Part
import sttp.model.{MediaType => SMediaType}
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter.{RawValue, RequestBody}
import sttp.tapir.{FileRange, InputStreamRange, RawBodyType}
import zio.http.FormField._
import zio.http.Request
import zio.http.{MediaType => ZMediaType}
import zio.stream.Stream
import zio.{RIO, Task, ZIO}

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer

class ZioHttpRequestBody[R](serverOptions: ZioHttpServerOptions[R]) extends RequestBody[RIO[R, *], ZioStreams] {
  override val streams: capabilities.Streams[ZioStreams] = ZioStreams

  override def toRaw[RAW](serverRequest: ServerRequest, bodyType: RawBodyType[RAW]): Task[RawValue[RAW]] = bodyType match {
    case RawBodyType.StringBody(defaultCharset) => asByteArray(serverRequest).map(new String(_, defaultCharset)).map(RawValue(_))
    case RawBodyType.ByteArrayBody              => asByteArray(serverRequest).map(RawValue(_))
    case RawBodyType.ByteBufferBody             => asByteArray(serverRequest).map(ByteBuffer.wrap).map(RawValue(_))
    case RawBodyType.InputStreamBody            => asByteArray(serverRequest).map(new ByteArrayInputStream(_)).map(RawValue(_))
    case RawBodyType.InputStreamRangeBody =>
      asByteArray(serverRequest).map(bytes => InputStreamRange(() => new ByteArrayInputStream(bytes))).map(RawValue(_))
    case RawBodyType.FileBody =>
      serverOptions.createFile(serverRequest).map(d => FileRange(d)).flatMap(file => ZIO.succeed(RawValue(file, Seq(file))))
    case RawBodyType.MultipartBody(_, _) =>
      zioHttpRequest(serverRequest).body.asMultipartForm
        .flatMap(form =>
          ZIO.foreach(form.formData) {
            case StreamingBinary(name, mediaType, _, filename, data) =>
              for {
                smt <- toSttp(mediaType)
                data <- data.runCollect
              } yield Part(name, data.toArray, Some(smt), filename)
            case Binary(name, data, mediaType, _, filename) =>
              toSttp(mediaType).map(smt => Part(name, data.toArray, Some(smt), filename))
            case Text(name, value, mediaType, filename) => toSttp(mediaType).map(smt => Part(name, value, Some(smt), filename))
            case Simple(name, value)                    => ZIO.succeed(Part(name, value, Some(sttp.model.MediaType.TextPlain)))
          }
        )
        .map(chunk => RawValue.fromParts(chunk.toSeq))
  }

  override def toStream(serverRequest: ServerRequest): streams.BinaryStream = stream(serverRequest).asInstanceOf[streams.BinaryStream]

  private def stream(serverRequest: ServerRequest): Stream[Throwable, Byte] =
    zioHttpRequest(serverRequest).body.asStream

  private def asByteArray(serverRequest: ServerRequest): Task[Array[Byte]] = zioHttpRequest(serverRequest).body.asArray

  private def zioHttpRequest(serverRequest: ServerRequest) = serverRequest.underlying.asInstanceOf[Request]

  private def toSttp(zmt: ZMediaType): Task[SMediaType] =
    ZIO
      .fromEither(
        SMediaType.safeApply(
          mainType = zmt.mainType,
          subType = zmt.subType,
          parameters = zmt.parameters
        )
      )
      .mapError(new IllegalArgumentException(_))
}
