package sttp.tapir.server.http4s

import cats.effect.{Async, Sync}
import cats.syntax.all._
import fs2.Chunk
import fs2.io.file.Files
import org.http4s.headers.{`Content-Disposition`, `Content-Type`}
import org.http4s.{Charset, EntityDecoder, Request, multipart}
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.{Header, Part}
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter.{RawValue, RequestBody}
import sttp.tapir.{FileRange, InputStreamRange, RawBodyType, RawPart}

import java.io.ByteArrayInputStream

private[http4s] class Http4sRequestBody[F[_]: Async](
    serverOptions: Http4sServerOptions[F]
) extends RequestBody[F, Fs2Streams[F]] {
  override val streams: Fs2Streams[F] = Fs2Streams[F]
  override def toRaw[R](serverRequest: ServerRequest, bodyType: RawBodyType[R]): F[RawValue[R]] = {
    val r = http4sRequest(serverRequest)
    toRawFromStream(serverRequest, r.body, bodyType, r.charset)
  }
  override def toStream(serverRequest: ServerRequest): streams.BinaryStream = http4sRequest(serverRequest).body

  private def http4sRequest(serverRequest: ServerRequest): Request[F] = serverRequest.underlying.asInstanceOf[Request[F]]

  private def toRawFromStream[R](
      serverRequest: ServerRequest,
      body: fs2.Stream[F, Byte],
      bodyType: RawBodyType[R],
      charset: Option[Charset]
  ): F[RawValue[R]] = {
    def asChunk: F[Chunk[Byte]] = body.compile.to(Chunk)
    def asByteArray: F[Array[Byte]] = body.compile.to(Chunk).map(_.toArray[Byte])

    bodyType match {
      case RawBodyType.StringBody(defaultCharset) =>
        asByteArray.map(new String(_, charset.map(_.nioCharset).getOrElse(defaultCharset))).map(RawValue(_))
      case RawBodyType.ByteArrayBody        => asByteArray.map(RawValue(_))
      case RawBodyType.ByteBufferBody       => asChunk.map(c => RawValue(c.toByteBuffer))
      case RawBodyType.InputStreamBody      => asByteArray.map(b => RawValue(new ByteArrayInputStream(b)))
      case RawBodyType.InputStreamRangeBody => asByteArray.map(b => RawValue(InputStreamRange(() => new ByteArrayInputStream(b))))

      case RawBodyType.FileBody =>
        serverOptions.createFile(serverRequest).flatMap { file =>
          val fileSink = Files[F].writeAll(file.toPath)
          body.through(fileSink).compile.drain.map(_ => RawValue(FileRange(file), Seq(FileRange(file))))
        }
      case m: RawBodyType.MultipartBody =>
        // TODO: use MultipartDecoder.mixedMultipart once available?
        implicitly[EntityDecoder[F, multipart.Multipart[F]]].decode(http4sRequest(serverRequest), strict = false).value.flatMap {
          case Left(failure) => Sync[F].raiseError(failure)
          case Right(mp) =>
            val rawPartsF: Vector[F[RawPart]] = mp.parts
              .flatMap(part => part.name.flatMap(name => m.partType(name)).map((part, _)).toList)
              .map { case (part, codecMeta) => toRawPart(serverRequest, part, codecMeta).asInstanceOf[F[RawPart]] }

            val rawParts: F[RawValue[Vector[RawPart]]] = rawPartsF.sequence.map { parts =>
              RawValue(parts, parts collect { case _ @Part(_, f: FileRange, _, _) => f })
            }

            rawParts.asInstanceOf[F[RawValue[R]]] // R is Vector[RawPart]
        }
    }
  }

  private def toRawPart[R](serverRequest: ServerRequest, part: multipart.Part[F], partType: RawBodyType[R]): F[Part[R]] = {
    val dispositionParams = part.headers.get[`Content-Disposition`].map(_.parameters).getOrElse(Map.empty)
    val charset = part.headers.get[`Content-Type`].flatMap(_.charset)
    toRawFromStream(serverRequest, part.body, partType, charset)
      .map(r =>
        Part(
          part.name.getOrElse(""),
          r.value,
          otherDispositionParams = dispositionParams.map { case (k, v) => k.toString -> v } - Part.NameDispositionParam,
          headers = part.headers.headers.map(h => Header(h.name.toString, h.value))
        )
      )
  }
}
