package sttp.tapir.server.akkahttp

import java.nio.charset.{Charset, StandardCharsets}

import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model.{StatusCode => AkkaStatusCode, _}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.StreamConverters
import akka.util.ByteString
import sttp.model.{Header, HeaderNames, Part}
import sttp.tapir.internal._
import sttp.tapir.server.internal.{EncodeOutputBody, EncodeOutputs, OutputValues}
import sttp.tapir.{CodecFormat, EndpointOutput, RawBodyType, RawPart}

import scala.util.Try

private[akkahttp] object OutputToAkkaRoute {
  private type EntityFromLength = Option[Long] => ResponseEntity

  def apply[O](defaultStatusCode: AkkaStatusCode, output: EndpointOutput[O], v: O): Route = {
    val outputValues = encodeOutputs(output, v, OutputValues.empty)

    val statusCode = outputValues.statusCode.map(c => AkkaStatusCode.int2StatusCode(c.code)).getOrElse(defaultStatusCode)
    val akkaHeaders = parseHeadersOrThrow(outputValues.headers)

    outputValues.body match {
      case Some(entityFromLength) =>
        val entity = entityFromLength(outputValues.contentLength)
        val entity2 = overrideContentTypeIfDefined(entity, akkaHeaders)
        complete(HttpResponse(entity = entity2, status = statusCode, headers = akkaHeaders))
      case None => complete(HttpResponse(statusCode, headers = akkaHeaders))
    }
  }

  // We can only create the entity once we know if its size is defined; depending on this, the body might end up
  // as a chunked or normal response. That's why here we return a function creating the entity basing on the length,
  // which might be only known when all other outputs are encoded.
  private val encodeOutputs: EncodeOutputs[EntityFromLength] = new EncodeOutputs(new EncodeOutputBody[EntityFromLength] {
    override def rawValueToBody(v: Any, format: CodecFormat, bodyType: RawBodyType[_]): EntityFromLength =
      contentLength =>
        rawValueToResponseEntity(bodyType.asInstanceOf[RawBodyType[Any]], formatToContentType(format, charset(bodyType)), contentLength, v)
    override def streamValueToBody(v: Any, format: CodecFormat, charset: Option[Charset]): EntityFromLength =
      contentLength => streamToEntity(formatToContentType(format, charset), contentLength, v.asInstanceOf[AkkaStream])
  })

  private def rawValueToResponseEntity[CF <: CodecFormat, R](
      bodyType: RawBodyType[R],
      ct: ContentType,
      contentLength: Option[Long],
      r: R
  ): ResponseEntity = {
    bodyType match {
      case RawBodyType.StringBody(charset) =>
        ct match {
          case nb: ContentType.NonBinary => HttpEntity(nb, r)
          case _                         => HttpEntity(ct, r.getBytes(charset))
        }
      case RawBodyType.ByteArrayBody   => HttpEntity(ct, r)
      case RawBodyType.ByteBufferBody  => HttpEntity(ct, ByteString(r))
      case RawBodyType.InputStreamBody => streamToEntity(ct, contentLength, StreamConverters.fromInputStream(() => r))
      case RawBodyType.FileBody        => HttpEntity.fromPath(ct, r.toPath)
      case m: RawBodyType.MultipartBody =>
        val parts = (r: Seq[RawPart]).flatMap(rawPartToBodyPart(m, _))
        val body = Multipart.FormData(parts: _*)
        body.toEntity()
    }
  }

  private def streamToEntity(contentType: ContentType, contentLength: Option[Long], stream: AkkaStream): ResponseEntity = {
    contentLength match {
      case None    => HttpEntity(contentType, stream)
      case Some(l) => HttpEntity(contentType, l, stream)
    }
  }

  private def rawPartToBodyPart[T](m: RawBodyType.MultipartBody, part: Part[T]): Option[Multipart.FormData.BodyPart] = {
    m.partType(part.name).map { partType =>
      val headers = part.headers.map {
        case Header(hk, hv) => parseHeaderOrThrow(hk, hv)
      }

      val partContentType = part.contentType.map(parseContentType).getOrElse(ContentTypes.`application/octet-stream`)
      val partContentLength = part.header(HeaderNames.ContentLength).flatMap(v => Try(v.toLong).toOption)
      val body = rawValueToResponseEntity(partType.asInstanceOf[RawBodyType[Any]], partContentType, partContentLength, part.body) match {
        case b: BodyPartEntity => overrideContentTypeIfDefined(b, headers)
        case _                 => throw new IllegalArgumentException(s"$partType is not supported in multipart bodies")
      }

      Multipart.FormData
        .BodyPart(part.name, body, part.otherDispositionParams, headers.filterNot(_.is(HeaderNames.ContentType.toLowerCase)).toList)
    }
  }

  private def formatToContentType(format: CodecFormat, charset: Option[Charset]): ContentType = {
    format match {
      case CodecFormat.Json()               => ContentTypes.`application/json`
      case CodecFormat.TextPlain()          => MediaTypes.`text/plain`.withCharset(charsetToHttpCharset(charset.getOrElse(StandardCharsets.UTF_8)))
      case CodecFormat.OctetStream()        => MediaTypes.`application/octet-stream`
      case CodecFormat.XWwwFormUrlencoded() => MediaTypes.`application/x-www-form-urlencoded`
      case CodecFormat.MultipartFormData()  => MediaTypes.`multipart/form-data`
      case f                                => parseContentType(f.mediaType.toString())

    }
  }

  private def parseContentType(ct: String): ContentType =
    ContentType.parse(ct).right.getOrElse(throw new IllegalArgumentException(s"Cannot parse content type: $ct"))

  private def charsetToHttpCharset(charset: Charset): HttpCharset = HttpCharset.custom(charset.name())

  private def parseHeadersOrThrow(kvs: Vector[(String, String)]): Vector[HttpHeader] = {
    kvs.map { case (k, v) => parseHeaderOrThrow(k, v) }
  }

  private def parseHeaderOrThrow(k: String, v: String): HttpHeader = HttpHeader.parse(k, v) match {
    case ParsingResult.Ok(h, _)     => h
    case ParsingResult.Error(error) => throw new IllegalArgumentException(s"Cannot parse header ($k, $v): $error")
  }

  private def overrideContentTypeIfDefined[RE <: ResponseEntity](re: RE, headers: Seq[HttpHeader]): RE = {
    import akka.http.scaladsl.model.headers.`Content-Type`
    headers
      .collectFirst {
        case `Content-Type`(ct) => ct
      }
      .map(ct => re.withContentType(ct).asInstanceOf[RE])
      .getOrElse(re)
  }
}
