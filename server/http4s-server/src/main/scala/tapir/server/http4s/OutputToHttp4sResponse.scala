package tapir.server.http4s

import cats.data._
import cats.implicits._
import cats.effect.{ContextShift, Sync}
import fs2.Chunk
import org.http4s
import org.http4s.headers.{`Content-Disposition`, `Content-Type`}
import org.http4s.util.CaseInsensitiveString
import org.http4s.{Charset, EntityBody, EntityEncoder, Header, Headers, multipart}
import tapir.internal._
import tapir.model.Part
import tapir.{
  ByteArrayValueType,
  ByteBufferValueType,
  CodecMeta,
  EndpointIO,
  EndpointOutput,
  FileValueType,
  InputStreamValueType,
  MediaType,
  MultipartValueType,
  RawPart,
  StatusCode,
  StreamingEndpointIO,
  StringValueType
}

class OutputToHttp4sResponse[F[_]: Sync: ContextShift](serverOptions: Http4sServerOptions[F]) {

  case class ResponseValues(body: Option[EntityBody[F]], headers: Vector[Header], statusCode: Option[StatusCode]) {
    def withBody(b: EntityBody[F], h: Header): ResponseValues = {
      if (body.isDefined) {
        throw new IllegalArgumentException("Body is already defined")
      }

      copy(body = Some(b), headers = headers :+ h)
    }

    def withHeaders(hs: Seq[Header]): ResponseValues = copy(headers = headers ++ hs)

    def withStatusCode(sc: StatusCode): ResponseValues = copy(statusCode = Some(sc))
  }

  def apply(output: EndpointOutput[_], v: Any): ResponseValues = {
    toResponse(output, v).runS(ResponseValues(None, Vector.empty, None)).value
  }

  private def toResponse(output: EndpointOutput[_], v: Any): State[ResponseValues, Unit] = {
    val vs = ParamsToSeq(v)

    val states = output.asVectorOfSingleOutputs.zipWithIndex.map {
      case (EndpointIO.Body(codec, _), i) =>
        codec.encode(vs(i)).map(rawValueToEntity(codec.meta, _)) match {
          case Some((entity, header)) => State.modify[ResponseValues](rv => rv.withBody(entity, header))
          case None                   => State.pure[ResponseValues, Unit](())
        }

      case (EndpointIO.StreamBodyWrapper(StreamingEndpointIO.Body(_, mediaType, _)), i) =>
        val ctHeader = mediaTypeToContentType(mediaType)
        State.modify[ResponseValues](rv => rv.withBody(vs(i).asInstanceOf[EntityBody[F]], ctHeader))

      case (EndpointIO.Header(name, codec, _), i) =>
        codec
          .encode(vs(i))
          .map((headerValue: String) => Header.Raw(CaseInsensitiveString(name), headerValue)) match {
          case Nil     => State.pure[ResponseValues, Unit](())
          case headers => State.modify[ResponseValues](rv => rv.withHeaders(headers))
        }

      case (EndpointIO.Headers(_), i) =>
        val headers = vs(i).asInstanceOf[Seq[(String, String)]].map(h => Header.Raw(CaseInsensitiveString(h._1), h._2))
        State.modify[ResponseValues](rv => rv.withHeaders(headers))

      case (EndpointIO.Mapped(wrapped, _, g, _), i) =>
        toResponse(wrapped, g(vs(i)))

      case (EndpointOutput.StatusCode(), i) =>
        State.modify[ResponseValues](rv => rv.withStatusCode(vs(i).asInstanceOf[StatusCode]))

      case (EndpointOutput.StatusFrom(io, default, _, when), i) =>
        val v = vs(i)
        val sc = when.find(_._1.matches(v)).map(_._2).getOrElse(default)
        toResponse(io, v).modify(_.withStatusCode(sc))

      case (EndpointOutput.Mapped(wrapped, _, g, _), i) =>
        toResponse(wrapped, g(vs(i)))
    }

    states.sequence_
  }

  def rawValueToEntity[M <: MediaType, R](codecMeta: CodecMeta[M, R], r: R): (EntityBody[F], Header) = {
    val ct: `Content-Type` = mediaTypeToContentType(codecMeta.mediaType)

    codecMeta.rawValueType match {
      case StringValueType(charset) =>
        val bytes = r.toString.getBytes(charset)
        fs2.Stream.chunk(Chunk.bytes(bytes)) -> ct
      case ByteArrayValueType  => fs2.Stream.chunk(Chunk.bytes(r)) -> ct
      case ByteBufferValueType => fs2.Stream.chunk(Chunk.byteBuffer(r)) -> ct
      case InputStreamValueType =>
        fs2.io.readInputStream(r.pure[F], serverOptions.ioChunkSize, serverOptions.blockingExecutionContext) -> ct
      case FileValueType => fs2.io.file.readAll(r.toPath, serverOptions.blockingExecutionContext, serverOptions.ioChunkSize) -> ct
      case mvt: MultipartValueType =>
        val parts = (r: Seq[RawPart]).flatMap(rawPartToBodyPart(mvt, _))
        val body = implicitly[EntityEncoder[F, multipart.Multipart[F]]].toEntity(multipart.Multipart(parts.toVector)).body
        body -> ct
    }
  }

  private def rawPartToBodyPart[T](mvt: MultipartValueType, part: Part[T]): Option[multipart.Part[F]] = {
    mvt.partCodecMeta(part.name).map { codecMeta =>
      val headers = part.headers.map {
        case (hk, hv) => Header.Raw(CaseInsensitiveString(hk), hv)
      }.toList

      val (entity, ctHeader) = rawValueToEntity(codecMeta.asInstanceOf[CodecMeta[_ <: MediaType, Any]], part.body)

      val contentDispositionHeader = `Content-Disposition`("form-data", part.otherDispositionParams + ("name" -> part.name))

      multipart.Part(Headers(ctHeader :: contentDispositionHeader :: headers), entity)
    }
  }

  private def mediaTypeToContentType(mediaType: MediaType): `Content-Type` =
    mediaType match {
      case MediaType.Json()               => `Content-Type`(http4s.MediaType.application.json)
      case MediaType.TextPlain(charset)   => `Content-Type`(http4s.MediaType.text.plain, Charset.fromNioCharset(charset))
      case MediaType.OctetStream()        => `Content-Type`(http4s.MediaType.application.`octet-stream`)
      case MediaType.XWwwFormUrlencoded() => `Content-Type`(http4s.MediaType.application.`x-www-form-urlencoded`)
      case MediaType.MultipartFormData()  => `Content-Type`(http4s.MediaType.multipart.`form-data`)
      case mt =>
        `Content-Type`(
          http4s.MediaType
            .parse(mt.mediaType)
            .right
            .getOrElse(throw new IllegalArgumentException(s"Cannot parse content type: $mediaType")))
    }
}
