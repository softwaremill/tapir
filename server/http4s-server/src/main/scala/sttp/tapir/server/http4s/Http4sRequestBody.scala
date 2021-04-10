package sttp.tapir.server.http4s

import java.io.ByteArrayInputStream
import cats.effect.Sync
import cats.syntax.all._
import cats.~>
import fs2.Chunk
import org.http4s.headers.{`Content-Disposition`, `Content-Type`}
import org.http4s.{Charset, EntityDecoder, Request, multipart}
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.{Header, Part}
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter.RequestBody
import sttp.tapir.{RawBodyType, RawPart}

private[http4s] class Http4sRequestBody[F[_]: Sync: ContextShift, G[_]: Sync]( // TODO: constraints?
    request: Request[F],
    serverRequest: ServerRequest,
    serverOptions: Http4sServerOptions[F, G],
    t: F ~> G
) extends RequestBody[G, Fs2Streams[F]] {
  override val streams: Fs2Streams[F] = Fs2Streams[F]
  override def toRaw[R](bodyType: RawBodyType[R]): G[R] = toRawFromStream(request.body, bodyType, request.charset)
  override def toStream(): streams.BinaryStream = request.body

  private def toRawFromStream[R](body: fs2.Stream[F, Byte], bodyType: RawBodyType[R], charset: Option[Charset]): G[R] = {
    def asChunk: G[Chunk[Byte]] = t(body.compile.to(Chunk))
    def asByteArray: G[Array[Byte]] = t(body.compile.to(Chunk).map(_.toByteBuffer.array()))

    bodyType match {
      case RawBodyType.StringBody(defaultCharset) => asByteArray.map(new String(_, charset.map(_.nioCharset).getOrElse(defaultCharset)))
      case RawBodyType.ByteArrayBody              => asByteArray
      case RawBodyType.ByteBufferBody             => asChunk.map(_.toByteBuffer)
      case RawBodyType.InputStreamBody            => asByteArray.map(new ByteArrayInputStream(_))
      case RawBodyType.FileBody =>
        serverOptions.createFile(serverRequest).flatMap { file =>
          val fileSink = fs2.io.file.writeAll[F](file.toPath, Blocker.liftExecutionContext(serverOptions.blockingExecutionContext))
          t(body.through(fileSink).compile.drain.map(_ => file))
        }
      case m: RawBodyType.MultipartBody =>
        // TODO: use MultipartDecoder.mixedMultipart once available?
        t(implicitly[EntityDecoder[F, multipart.Multipart[F]]].decode(request, strict = false).value.flatMap {
          case Left(failure) => Sync[F].raiseError(failure)
          case Right(mp) =>
            val rawPartsF: Vector[F[RawPart]] = mp.parts
              .flatMap(part => part.name.flatMap(name => m.partType(name)).map((part, _)).toList)
              .map { case (part, codecMeta) => toRawPart(part, codecMeta).asInstanceOf[F[RawPart]] }

            val rawParts: F[Vector[RawPart]] = rawPartsF.sequence

            rawParts.asInstanceOf[F[R]] // R is Seq[RawPart]
        })
    }
  }

  private def toRawPart[R](part: multipart.Part[F], partType: RawBodyType[R]): G[Part[R]] = {
    val dispositionParams = part.headers.get(`Content-Disposition`).map(_.parameters).getOrElse(Map.empty)
    val charset = part.headers.get(`Content-Type`).flatMap(_.charset)
    toRawFromStream(part.body, partType, charset)
      .map(r =>
        Part(
          part.name.getOrElse(""),
          r,
          otherDispositionParams = dispositionParams - Part.NameDispositionParam,
          headers = part.headers.toList.map(h => Header(h.name.value, h.value))
        )
      )
  }
}
