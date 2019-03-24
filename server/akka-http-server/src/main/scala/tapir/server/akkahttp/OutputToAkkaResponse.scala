package tapir.server.akkahttp

import java.nio.charset.Charset

import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model._
import akka.stream.scaladsl.StreamConverters
import akka.util.ByteString
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

private[akkahttp] object OutputToAkkaResponse {

  case class ResponseValues(body: Option[ResponseEntity], headers: Vector[HttpHeader], statusCode: Option[StatusCode]) {
    def withBody(b: ResponseEntity): ResponseValues = {
      if (body.isDefined) {
        throw new IllegalArgumentException("Body is already defined")
      }

      copy(body = Some(b))
    }

    def withHeader(h: HttpHeader): ResponseValues = copy(headers = headers :+ h)

    def withStatusCode(sc: StatusCode): ResponseValues = copy(statusCode = Some(sc))
  }

  def apply(output: EndpointOutput[_], v: Any): ResponseValues = apply(output, v, ResponseValues(None, Vector.empty, None))

  private def apply(output: EndpointOutput[_], v: Any, initialResponseValues: ResponseValues): ResponseValues = {
    val vs = ParamsToSeq(v)
    var rv = initialResponseValues

    output.asVectorOfSingleOutputs.zipWithIndex.foreach {
      case (EndpointIO.Body(codec, _), i) =>
        codec.encode(vs(i)).map(rawValueToResponseEntity(codec.meta, _)).foreach(re => rv = rv.withBody(re))
      case (EndpointIO.StreamBodyWrapper(StreamingEndpointIO.Body(_, mediaType, _)), i) =>
        val re = HttpEntity(mediaTypeToContentType(mediaType), vs(i).asInstanceOf[AkkaStream])
        rv = rv.withBody(re)
      case (EndpointIO.Header(name, codec, _), i) =>
        codec
          .encode(vs(i))
          .map(parseHeaderOrThrow(name, _))
          .foreach(h => rv = rv.withHeader(h))
      case (EndpointIO.Headers(_), i) =>
        vs(i)
          .asInstanceOf[Seq[(String, String)]]
          .map(h => parseHeaderOrThrow(h._1, h._2))
          .foreach(h => rv = rv.withHeader(h))
      case (EndpointIO.Mapped(wrapped, _, g, _), i) =>
        rv = apply(wrapped, g(vs(i)), rv)

      case (EndpointOutput.StatusCode(), i) =>
        rv = rv.withStatusCode(vs(i).asInstanceOf[StatusCode])
      case (EndpointOutput.StatusFrom(io, default, _, when), i) =>
        val v = vs(i)
        val sc = when.find(_._1.matches(v)).map(_._2).getOrElse(default)
        rv = apply(io, v, rv.withStatusCode(sc))
      case (EndpointOutput.Mapped(wrapped, _, g, _), i) =>
        rv = apply(wrapped, g(vs(i)), rv)
    }

    rv
  }

  def rawValueToResponseEntity[M <: MediaType, R](codecMeta: CodecMeta[M, R], r: R): ResponseEntity = {
    val ct = mediaTypeToContentType(codecMeta.mediaType)
    codecMeta.rawValueType match {
      case StringValueType(charset) =>
        ct match {
          case nb: ContentType.NonBinary => HttpEntity(nb, r)
          case _                         => HttpEntity(ct, r.getBytes(charset))
        }
      case ByteArrayValueType   => HttpEntity(ct, r)
      case ByteBufferValueType  => HttpEntity(ct, ByteString(r))
      case InputStreamValueType => HttpEntity(ct, StreamConverters.fromInputStream(() => r))
      case FileValueType        => HttpEntity.fromPath(ct, r.toPath)
      case mvt: MultipartValueType =>
        val parts = (r: Seq[RawPart]).flatMap(rawPartToBodyPart(mvt, _))
        val body = Multipart.FormData(parts: _*)
        body.toEntity()
    }
  }

  private def rawPartToBodyPart[T](mvt: MultipartValueType, part: Part[T]): Option[Multipart.FormData.BodyPart] = {
    mvt.partCodecMeta(part.name).map { codecMeta =>
      val headers = part.headers.map {
        case (hk, hv) => parseHeaderOrThrow(hk, hv)
      }

      val body = rawValueToResponseEntity(codecMeta.asInstanceOf[CodecMeta[_ <: MediaType, Any]], part.body) match {
        case b: BodyPartEntity => b
        case _                 => throw new IllegalArgumentException(s"${codecMeta.rawValueType} is not supported in multipart bodies")
      }

      Multipart.FormData.BodyPart(part.name, body, part.otherDispositionParams, headers.toList)
    }
  }

  private def mediaTypeToContentType(mediaType: MediaType): ContentType = {
    mediaType match {
      case MediaType.Json()               => ContentTypes.`application/json`
      case MediaType.TextPlain(charset)   => MediaTypes.`text/plain`.withCharset(charsetToHttpCharset(charset))
      case MediaType.OctetStream()        => MediaTypes.`application/octet-stream`
      case MediaType.XWwwFormUrlencoded() => MediaTypes.`application/x-www-form-urlencoded`.withMissingCharset
      case MediaType.MultipartFormData()  => MediaTypes.`multipart/form-data`
      case mt =>
        ContentType.parse(mt.mediaType).right.getOrElse(throw new IllegalArgumentException(s"Cannot parse content type: $mediaType"))
    }
  }

  private def charsetToHttpCharset(charset: Charset): HttpCharset = HttpCharset.custom(charset.name())

  private def parseHeaderOrThrow(k: String, v: String): HttpHeader = HttpHeader.parse(k, v) match {
    case ParsingResult.Ok(h, _)     => h
    case ParsingResult.Error(error) => throw new IllegalArgumentException(s"Cannot parse header ($k, $v): $error")
  }
}
