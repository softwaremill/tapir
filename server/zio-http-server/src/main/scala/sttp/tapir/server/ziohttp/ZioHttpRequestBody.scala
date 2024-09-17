package sttp.tapir.server.ziohttp

import sttp.capabilities
import sttp.capabilities.zio.ZioStreams
import sttp.model.Part
import sttp.model.Part.FileNameDispositionParam
import sttp.tapir.FileRange
import sttp.tapir.InputStreamRange
import sttp.tapir.RawBodyType
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter.RawValue
import sttp.tapir.server.interpreter.RequestBody
import zio.{RIO, Task, ZIO}
import zio.http.{FormField, Request, StreamingForm}
import zio.http.FormField.StreamingBinary
import zio.stream.ZSink
import zio.stream.ZStream

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer

class ZioHttpRequestBody[R](serverOptions: ZioHttpServerOptions[R]) extends RequestBody[RIO[R, *], ZioStreams] {
  override val streams: capabilities.Streams[ZioStreams] = ZioStreams

  override def toRaw[RAW](serverRequest: ServerRequest, bodyType: RawBodyType[RAW], maxBytes: Option[Long]): Task[RawValue[RAW]] =
    toRaw(serverRequest, zStream(serverRequest), bodyType, maxBytes)

  private def toRaw[RAW](
      serverRequest: ServerRequest,
      stream: ZStream[Any, Throwable, Byte],
      bodyType: RawBodyType[RAW],
      maxBytes: Option[Long]
  ): Task[RawValue[RAW]] = {
    val limitedStream = limitedZStream(stream, maxBytes)
    val asByteArray = limitedStream.runCollect.map(_.toArray)

    bodyType match {
      case RawBodyType.StringBody(defaultCharset) => asByteArray.map(new String(_, defaultCharset)).map(RawValue(_))
      case RawBodyType.ByteArrayBody              => asByteArray.map(RawValue(_))
      case RawBodyType.ByteBufferBody             => asByteArray.map(bytes => ByteBuffer.wrap(bytes)).map(RawValue(_))
      case RawBodyType.InputStreamBody            => asByteArray.map(new ByteArrayInputStream(_)).map(RawValue(_))
      case RawBodyType.InputStreamRangeBody =>
        asByteArray.map(bytes => InputStreamRange(() => new ByteArrayInputStream(bytes))).map(RawValue(_))
      case RawBodyType.FileBody =>
        for {
          file <- serverOptions.createFile(serverRequest)
          _ <- limitedStream.run(ZSink.fromFile(file)).unit
        } yield RawValue(FileRange(file), Seq(FileRange(file)))
      case m: RawBodyType.MultipartBody => handleMultipartBody(serverRequest, m, limitedStream)
    }
  }

  private def handleMultipartBody[RAW](
      serverRequest: ServerRequest,
      bodyType: RawBodyType.MultipartBody,
      limitedStream: ZStream[Any, Throwable, Byte]
  ): Task[RawValue[RAW]] = {
    zRequest(serverRequest).body.contentType.flatMap(_.boundary) match {
      case Some(boundary) =>
        StreamingForm(limitedStream, boundary).fields
          .flatMap(field => ZStream.fromIterable(bodyType.partType(field.name).map((field, _))))
          .mapZIO { case (field, bodyType) => toRawPart(serverRequest, field, bodyType) }
          .runCollect
          .map(RawValue.fromParts(_).asInstanceOf[RawValue[RAW]])
      case None =>
        ZIO.fail(
          new IllegalStateException("Cannot decode body as streaming multipart/form-data without a known boundary")
        )
    }
  }

  private def toRawPart[A](serverRequest: ServerRequest, field: FormField, bodyType: RawBodyType[A]): Task[Part[A]] = {
    val fieldsStream = field match {
      case StreamingBinary(_, _, _, _, s) => s
      case _                              => ZStream.fromIterableZIO(field.asChunk)
    }
    toRaw(serverRequest, fieldsStream, bodyType, None)
      .map(raw =>
        Part(
          field.name,
          raw.value,
          otherDispositionParams = field.filename.map(name => Map(FileNameDispositionParam -> name)).getOrElse(Map.empty)
        ).contentType(field.contentType.fullType)
      )
  }

  override def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): streams.BinaryStream =
    limitedZStream(zStream(serverRequest), maxBytes).asInstanceOf[streams.BinaryStream]

  private def zRequest(serverRequest: ServerRequest) = serverRequest.underlying.asInstanceOf[Request]

  private def limitedZStream(stream: ZStream[Any, Throwable, Byte], maxBytes: Option[Long]) = {
    maxBytes.map(ZioStreams.limitBytes(stream, _)).getOrElse(stream)
  }

  private def zStream(serverRequest: ServerRequest) = zRequest(serverRequest).body.asStream
}
