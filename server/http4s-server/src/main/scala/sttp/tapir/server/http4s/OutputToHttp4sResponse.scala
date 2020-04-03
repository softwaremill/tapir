package sttp.tapir.server.http4s

import java.nio.charset.StandardCharsets

import cats.effect.{Blocker, ContextShift, Sync}
import cats.implicits._
import fs2.Chunk
import org.http4s
import org.http4s.headers.{`Content-Disposition`, `Content-Type`}
import org.http4s.util.CaseInsensitiveString
import org.http4s.{Charset, EntityBody, EntityEncoder, Header, Headers, Response, Status, multipart}
import sttp.model.{Part, Header => SttpHeader}
import sttp.tapir.internal._
import sttp.tapir.server.internal.{EncodeOutputBody, EncodeOutputs, OutputValues}
import sttp.tapir.{CodecFormat, EndpointOutput, RawBodyType, RawPart}

class OutputToHttp4sResponse[F[_]: Sync: ContextShift](serverOptions: Http4sServerOptions[F]) {
  def apply[O](defaultStatusCode: sttp.model.StatusCode, output: EndpointOutput[O], v: O): Response[F] = {
    val outputValues = encodeOutputs(output, v, OutputValues.empty)
    val statusCode = outputValues.statusCode.map(statusCodeToHttp4sStatus).getOrElse(statusCodeToHttp4sStatus(defaultStatusCode))

    val headers = allOutputHeaders(outputValues)
    outputValues.body match {
      case Some((entity, _)) => Response(status = statusCode, headers = headers, body = entity)
      case None              => Response(status = statusCode, headers = headers)
    }
  }

  private def statusCodeToHttp4sStatus(code: sttp.model.StatusCode): Status =
    Status.fromInt(code.code).right.getOrElse(throw new IllegalArgumentException(s"Invalid status code: $code"))

  private def allOutputHeaders(outputValues: OutputValues[(EntityBody[F], Header)]): Headers = {
    val headers = outputValues.headers.map { case (k, v) => Header.Raw(CaseInsensitiveString(k), v) }
    val shouldAddCtHeader = !headers.exists(_.name == `Content-Type`.name)
    outputValues.body match {
      case Some((_, ctHeader)) if shouldAddCtHeader => Headers.of(headers :+ ctHeader: _*)
      case _                                        => Headers.of(headers: _*)
    }
  }

  private val encodeOutputs: EncodeOutputs[(EntityBody[F], Header)] = new EncodeOutputs(new EncodeOutputBody[(EntityBody[F], Header)] {
    override def rawValueToBody(v: Any, format: CodecFormat, bodyType: RawBodyType[_]): (EntityBody[F], Header) =
      rawValueToEntity(bodyType.asInstanceOf[RawBodyType[Any]], formatToContentType(format, charset(bodyType)), v)
    override def streamValueToBody(v: Any, format: CodecFormat, charset: Option[java.nio.charset.Charset]): (EntityBody[F], Header) = {
      val ctHeader = formatToContentType(format, charset)
      (v.asInstanceOf[EntityBody[F]], ctHeader)
    }
  })

  private def rawValueToEntity[CF <: CodecFormat, R](bodyType: RawBodyType[R], ct: `Content-Type`, r: R): (EntityBody[F], Header) = {
    bodyType match {
      case RawBodyType.StringBody(charset) =>
        val bytes = r.toString.getBytes(charset)
        fs2.Stream.chunk(Chunk.bytes(bytes)) -> ct
      case RawBodyType.ByteArrayBody  => fs2.Stream.chunk(Chunk.bytes(r)) -> ct
      case RawBodyType.ByteBufferBody => fs2.Stream.chunk(Chunk.byteBuffer(r)) -> ct
      case RawBodyType.InputStreamBody =>
        fs2.io.readInputStream(r.pure[F], serverOptions.ioChunkSize, Blocker.liftExecutionContext(serverOptions.blockingExecutionContext)) -> ct
      case RawBodyType.FileBody =>
        fs2.io.file.readAll(r.toPath, Blocker.liftExecutionContext(serverOptions.blockingExecutionContext), serverOptions.ioChunkSize) -> ct
      case m: RawBodyType.MultipartBody =>
        val parts = (r: Seq[RawPart]).flatMap(rawPartToBodyPart(m, _))
        val body = implicitly[EntityEncoder[F, multipart.Multipart[F]]].toEntity(multipart.Multipart(parts.toVector)).body
        body -> ct
    }
  }

  private def rawPartToBodyPart[T](m: RawBodyType.MultipartBody, part: Part[T]): Option[multipart.Part[F]] = {
    m.partType(part.name).map { partType =>
      val headers = part.headers.map {
        case SttpHeader(hk, hv) => Header.Raw(CaseInsensitiveString(hk), hv)
      }.toList

      val partContentType = part.contentType.map(parseContentType).getOrElse(`Content-Type`(http4s.MediaType.application.`octet-stream`))
      val (entity, ctHeader) = rawValueToEntity(partType.asInstanceOf[RawBodyType[Any]], partContentType, part.body)

      val dispositionParams = part.otherDispositionParams + (Part.NameDispositionParam -> part.name)
      val contentDispositionHeader = `Content-Disposition`("form-data", dispositionParams)

      val shouldAddCtHeader = headers.exists(_.name == `Content-Type`.name)
      val allHeaders = if (shouldAddCtHeader) {
        Headers(ctHeader :: contentDispositionHeader :: headers)
      } else {
        Headers(contentDispositionHeader :: headers)
      }

      multipart.Part(allHeaders, entity)
    }
  }

  private def formatToContentType(format: CodecFormat, charset: Option[java.nio.charset.Charset]): `Content-Type` =
    format match {
      case CodecFormat.Json() => `Content-Type`(http4s.MediaType.application.json)
      case CodecFormat.TextPlain() =>
        `Content-Type`(http4s.MediaType.text.plain, Charset.fromNioCharset(charset.getOrElse(StandardCharsets.UTF_8)))
      case CodecFormat.OctetStream()        => `Content-Type`(http4s.MediaType.application.`octet-stream`)
      case CodecFormat.XWwwFormUrlencoded() => `Content-Type`(http4s.MediaType.application.`x-www-form-urlencoded`)
      case CodecFormat.MultipartFormData()  => `Content-Type`(http4s.MediaType.multipart.`form-data`)
      case f                                => parseContentType(f.mediaType.toString())
    }

  private def parseContentType(ct: String): `Content-Type` =
    `Content-Type`(
      http4s.MediaType
        .parse(ct)
        .right
        .getOrElse(throw new IllegalArgumentException(s"Cannot parse content type: $ct"))
    )
}
