package sttp.tapir.server.ziohttp

import sttp.{capabilities, model}
import sttp.capabilities.zio.ZioStreams
import sttp.model.Part
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter.{RawValue, RequestBody}
import sttp.tapir.{FileRange, InputStreamRange, RawBodyType, RawPart}
import zio.http.FormField.StreamingBinary
import zio.http.{FormField, Request}
import zio.stream.{Stream, ZSink, ZStream}
import zio.{RIO, Task, ZIO}

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import scala.collection.immutable
import sttp.tapir.RawBodyType.FileBody
import zio.http.multipart.mixed.MultipartMixed

class ZioHttpRequestBody[R](serverOptions: ZioHttpServerOptions[R]) extends RequestBody[RIO[R, *], ZioStreams] {
  override val streams: capabilities.Streams[ZioStreams] = ZioStreams

  override def toRaw[RAW](serverRequest: ServerRequest, bodyType: RawBodyType[RAW], maxBytes: Option[Long]): Task[RawValue[RAW]] = {

    def asByteArray: Task[Array[Byte]] =
      (toStream(serverRequest, maxBytes).asInstanceOf[ZStream[Any, Throwable, Byte]]).runCollect.map(_.toArray)

    bodyType match {
      case RawBodyType.StringBody(defaultCharset) => asByteArray.map(new String(_, defaultCharset)).map(RawValue(_))
      case RawBodyType.ByteArrayBody              => asByteArray.map(RawValue(_))
      case RawBodyType.ByteBufferBody             => asByteArray.map(bytes => ByteBuffer.wrap(bytes)).map(RawValue(_))
      case RawBodyType.InputStreamBody            => asByteArray.map(new ByteArrayInputStream(_)).map(RawValue(_))
      case RawBodyType.InputStreamRangeBody =>
        asByteArray.map(bytes => new InputStreamRange(() => new ByteArrayInputStream(bytes))).map(RawValue(_))
      case RawBodyType.FileBody =>
        for {
          file <- serverOptions.createFile(serverRequest)
          _ <- (toStream(serverRequest, maxBytes)
            .asInstanceOf[ZStream[Any, Throwable, Byte]])
            .run(ZSink.fromFile(file))
            .map(_ => ())
        } yield RawValue(FileRange(file), Seq(FileRange(file)))
      case RawBodyType.MultipartBody(partTypes, _) => toRawMultipart(serverRequest, partTypes)
    }
  }

  override def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): streams.BinaryStream = {
    val inputStream = stream(serverRequest)
    maxBytes.map(ZioStreams.limitBytes(inputStream, _)).getOrElse(inputStream).asInstanceOf[streams.BinaryStream]
  }

  private def stream(serverRequest: ServerRequest): Stream[Throwable, Byte] =
    zioHttpRequest(serverRequest).body.asStream

  private def zioHttpRequest(serverRequest: ServerRequest) = serverRequest.underlying.asInstanceOf[Request]

  private def decodeFilePart(part: MultipartMixed.Part, filename: Option[String]): Task[model.Part[FileRange]] =
    for {
      file <- ZIO.attemptBlocking(Files.createTempFile("upload-", ".tmp"))
      sink = ZSink.fromFile(file.toFile)
      _ <- part.bytes.run(sink)
    } yield model.Part(name = "file", body = FileRange(file.toFile), fileName = filename)

  private def decodeTextPart(part: MultipartMixed.Part, fieldName: String): Task[model.Part[String]] =
    part.toBody.asString.map(body => model.Part(name = fieldName, body = body))

  private def toRawMultipart(
                              serverRequest: ServerRequest,
                              partTypes: Map[String, RawBodyType[_]]
                            ): Task[RawValue[Seq[Part[Any]]]] = // Task[RawValue[Seq[Part[String | FileRange]]]]
  {
    def isFile(formField: FormField): Boolean = partTypes.getOrElse(
      formField.name,
      throw new IllegalArgumentException(s"Can't find the part type with name ${formField.name}")
    ) match {
      case FileBody => true
      case _        => false
    }

    ZStream
      // we use `asMultipartMixed` because it gives access to the raw data...
      .fromZIO(zioHttpRequest(serverRequest).body.asMultipartMixed)
      .flatMap(_.parts)
      .zip(
        // ...and we use `asMultipartFormStream` for the form field name and the filename only because
        // the file content appears to be decoded wrongly by `asMultipartFormStream`
        ZStream.fromZIO(zioHttpRequest(serverRequest).body.asMultipartFormStream).flatMap(_.fields)
      )
      .flatMap {
        case (part, metadata) if isFile(metadata) =>
          ZStream.fromZIO(decodeFilePart(part, filename = metadata.filename))
        case (part, metadata) =>
          ZStream.fromZIO(decodeTextPart(part, fieldName = metadata.name))
      }
      .runCollect
      .map(RawValue(_))
  }

}
