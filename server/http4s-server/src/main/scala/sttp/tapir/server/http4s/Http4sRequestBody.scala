package sttp.tapir.server.http4s

import cats.effect.{Async, Sync}
import cats.syntax.all._
import cats.{Monad, ~>}
import fs2.Chunk
import fs2.io.file.Files
import org.http4s.headers.{`Content-Disposition`, `Content-Type`}
import org.http4s.{Charset, EntityDecoder, Request, multipart}
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.{Header, Part}
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter.{RawValue, RequestBody}
import sttp.tapir.{RawBodyType, RawPart, TapirFile}

import java.io.ByteArrayInputStream

private[http4s] class Http4sRequestBody[F[_]: Async, G[_]: Monad](
    request: Request[F],
    serverRequest: ServerRequest,
    serverOptions: Http4sServerOptions[F, G],
    t: F ~> G
) extends RequestBody[G, Fs2Streams[F]] {
  override val streams: Fs2Streams[F] = Fs2Streams[F]
  override def toRaw[R](bodyType: RawBodyType[R]): G[RawValue[R]] = toRawFromStream(request.body, bodyType, request.charset)
  override def toStream(): streams.BinaryStream = request.body

  private def toRawFromStream[R](body: fs2.Stream[F, Byte], bodyType: RawBodyType[R], charset: Option[Charset]): G[RawValue[R]] = {
    def asChunk: G[Chunk[Byte]] = t(body.compile.to(Chunk))
    def asByteArray: G[Array[Byte]] = t(body.compile.to(Chunk).map(_.toArray[Byte]))

    bodyType match {
      case RawBodyType.StringBody(defaultCharset) =>
        asByteArray.map(new String(_, charset.map(_.nioCharset).getOrElse(defaultCharset))).map(RawValue(_))
      case RawBodyType.ByteArrayBody   => asByteArray.map(RawValue(_))
      case RawBodyType.ByteBufferBody  => asChunk.map(c => RawValue(c.toByteBuffer))
      case RawBodyType.InputStreamBody => asByteArray.map(b => RawValue(new ByteArrayInputStream(b)))
      case RawBodyType.FileBody =>
        serverOptions.createFile(serverRequest).flatMap { file =>
          val fileSink = Files[F].writeAll(file.toPath)
          t(body.through(fileSink).compile.drain.map(_ => RawValue(file, Seq(file))))
        }
      case m: RawBodyType.MultipartBody =>
        // TODO: use MultipartDecoder.mixedMultipart once available?
        t(implicitly[EntityDecoder[F, multipart.Multipart[F]]].decode(request, strict = false).value.flatMap {
          case Left(failure) => Sync[F].raiseError(failure)
          case Right(mp) =>
            val rawPartsF: Vector[F[RawPart]] = mp.parts
              .flatMap(part => part.name.flatMap(name => m.partType(name)).map((part, _)).toList)
              .map { case (part, codecMeta) => toRawPart(part, codecMeta).asInstanceOf[F[RawPart]] }

            val rawParts: F[RawValue[Vector[RawPart]]] = rawPartsF.sequence.map { parts =>
              RawValue(parts, parts collect { case _ @Part(_, f: TapirFile, _, _) => f })
            }

            rawParts.asInstanceOf[F[RawValue[R]]] // R is Vector[RawPart]
        })
    }
  }

  private def toRawPart[R](part: multipart.Part[F], partType: RawBodyType[R]): G[Part[R]] = {
    val dispositionParams = part.headers.get[`Content-Disposition`].map(_.parameters).getOrElse(Map.empty)
    val charset = part.headers.get[`Content-Type`].flatMap(_.charset)
    toRawFromStream(part.body, partType, charset)
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
