package sttp.tapir.server.http4s

import java.io.ByteArrayInputStream

import cats.effect.{Blocker, ContextShift, Sync}
import cats.implicits._
import fs2.Chunk
import org.http4s.headers.{`Content-Disposition`, `Content-Type`}
import org.http4s.{Charset, EntityDecoder, Request, multipart}
import sttp.model.{Header, Part}
import sttp.tapir.{
  ByteArrayValueType,
  ByteBufferValueType,
  CodecMeta,
  FileValueType,
  InputStreamValueType,
  MultipartValueType,
  RawPart,
  RawValueType,
  StringValueType
}

class Http4sRequestToRawBody[F[_]: Sync: ContextShift](serverOptions: Http4sServerOptions[F]) {
  def apply[R](body: fs2.Stream[F, Byte], rawBodyType: RawValueType[R], charset: Option[Charset], req: Request[F]): F[R] = {
    def asChunk: F[Chunk[Byte]] = body.compile.to(Chunk)
    def asByteArray: F[Array[Byte]] = body.compile.to(Chunk).map(_.toByteBuffer.array())

    rawBodyType match {
      case StringValueType(defaultCharset) => asByteArray.map(new String(_, charset.map(_.nioCharset).getOrElse(defaultCharset)))
      case ByteArrayValueType              => asByteArray
      case ByteBufferValueType             => asChunk.map(_.toByteBuffer)
      case InputStreamValueType            => asByteArray.map(new ByteArrayInputStream(_))
      case FileValueType =>
        serverOptions.createFile(serverOptions.blockingExecutionContext, req).flatMap { file =>
          val fileSink = fs2.io.file.writeAll(file.toPath, Blocker.liftExecutionContext(serverOptions.blockingExecutionContext))
          body.through(fileSink).compile.drain.map(_ => file)
        }
      case mvt: MultipartValueType =>
        // TODO: use MultipartDecoder.mixedMultipart once available?
        implicitly[EntityDecoder[F, multipart.Multipart[F]]].decode(req, strict = false).value.flatMap {
          case Left(failure) =>
            throw new IllegalArgumentException("Cannot decode multipart body: " + failure) // TODO
          case Right(mp) =>
            val rawPartsF: Vector[F[RawPart]] = mp.parts
              .flatMap(part => part.name.flatMap(name => mvt.partCodecMeta(name)).map((part, _)).toList)
              .map { case (part, codecMeta) => toRawPart(part, codecMeta, req).asInstanceOf[F[RawPart]] }

            val rawParts: F[Vector[RawPart]] = rawPartsF.sequence

            rawParts.asInstanceOf[F[R]] // R is Seq[RawPart]
        }
    }
  }

  private def toRawPart[R](part: multipart.Part[F], codecMeta: CodecMeta[_, _, R], req: Request[F]): F[Part[R]] = {
    val dispositionParams = part.headers.get(`Content-Disposition`).map(_.parameters).getOrElse(Map.empty)
    val charset = part.headers.get(`Content-Type`).flatMap(_.charset)
    apply(part.body, codecMeta.rawValueType, charset, req)
      .map(r =>
        Part(
          part.name.getOrElse(""),
          r,
          otherDispositionParams = dispositionParams - Part.NameDispositionParam,
          headers = part.headers.toList.map(h => Header.notValidated(h.name.value, h.value))
        )
      )
  }
}
