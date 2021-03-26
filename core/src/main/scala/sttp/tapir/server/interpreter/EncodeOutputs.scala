package sttp.tapir.server.interpreter

import sttp.model.{HasHeaders, Header, HeaderNames, MediaType, StatusCode}
import sttp.tapir.RawBodyType.StringBody
import sttp.tapir.internal.{Params, ParamsAsAny, SplitParams}
import sttp.tapir.{Codec, CodecFormat, EndpointIO, EndpointOutput, Mapping, RawBodyType, StreamBodyIO, WebSocketBodyOutput}

import java.nio.charset.Charset
import scala.collection.immutable.Seq

class EncodeOutputs[B, S](rawToResponseBody: ToResponseBody[B, S]) {
  def apply(output: EndpointOutput[_], value: Params, ov: OutputValues[B]): OutputValues[B] = {
    output match {
      case s: EndpointIO.Single[_]                    => applySingle(s, value, ov)
      case s: EndpointOutput.Single[_]                => applySingle(s, value, ov)
      case EndpointIO.Pair(left, right, _, split)     => applyPair(left, right, split, value, ov)
      case EndpointOutput.Pair(left, right, _, split) => applyPair(left, right, split, value, ov)
      case EndpointOutput.Void()                      => throw new IllegalArgumentException("Cannot encode a void output!")
    }
  }

  private def applyPair(
      left: EndpointOutput[_],
      right: EndpointOutput[_],
      split: SplitParams,
      params: Params,
      ov: OutputValues[B]
  ): OutputValues[B] = {
    val (leftParams, rightParams) = split(params)
    apply(right, rightParams, apply(left, leftParams, ov))
  }

  private def applySingle(output: EndpointOutput.Single[_], value: Params, ov: OutputValues[B]): OutputValues[B] = {
    def encodedC[T](codec: Codec[_, _, _ <: CodecFormat]): T = codec.asInstanceOf[Codec[T, Any, CodecFormat]].encode(value.asAny)
    def encodedM[T](mapping: Mapping[_, _]): T = mapping.asInstanceOf[Mapping[T, Any]].encode(value.asAny)
    output match {
      case EndpointIO.Empty(_, _)                   => ov
      case EndpointOutput.FixedStatusCode(sc, _, _) => ov.withStatusCode(sc)
      case EndpointIO.FixedHeader(header, _, _)     => ov.withHeader(header.name, header.value)
      case EndpointIO.Body(rawBodyType, codec, _) =>
        ov.withBody(headers => rawToResponseBody.fromRawValue(encodedC[Any](codec), headers, codec.format, rawBodyType))
          .withDefaultContentType(codec.format, charset(codec.format.mediaType, rawBodyType))
      case EndpointIO.StreamBodyWrapper(StreamBodyIO(_, codec, _, charset)) =>
        ov.withBody(headers => rawToResponseBody.fromStreamValue(encodedC(codec), headers, codec.format, charset))
          .withDefaultContentType(codec.format, charset)
          .withHeaderTransformation(hs =>
            if (hs.exists(_.is(HeaderNames.ContentLength))) hs else hs :+ Header(HeaderNames.TransferEncoding, "chunked")
          )
      case EndpointIO.Header(name, codec, _) =>
        encodedC[List[String]](codec).foldLeft(ov) { case (ovv, headerValue) => ovv.withHeader(name, headerValue) }
      case EndpointIO.Headers(codec, _) =>
        encodedC[List[sttp.model.Header]](codec).foldLeft(ov)((ov2, h) => ov2.withHeader(h.name, h.value))
      case EndpointIO.MappedPair(wrapped, mapping) => apply(wrapped, ParamsAsAny(encodedM[Any](mapping)), ov)
      case EndpointOutput.StatusCode(_, codec, _)  => ov.withStatusCode(encodedC[StatusCode](codec))
      case EndpointOutput.WebSocketBodyWrapper(o) =>
        ov.withBody(_ =>
          rawToResponseBody.fromWebSocketPipe(
            encodedC[rawToResponseBody.streams.Pipe[Any, Any]](o.codec),
            o.asInstanceOf[WebSocketBodyOutput[rawToResponseBody.streams.Pipe[Any, Any], Any, Any, Any, S]]
          )
        )
      case EndpointOutput.OneOf(mappings, mapping) =>
        val enc = encodedM[Any](mapping)
        val statusMapping = mappings
          .find(m => m.appliesTo(enc))
          .getOrElse(throw new IllegalArgumentException(s"No status code mapping for value: $enc, in output: $output"))
        apply(statusMapping.output, ParamsAsAny(enc), statusMapping.statusCode.map(ov.withStatusCode).getOrElse(ov))
      case EndpointOutput.MappedPair(wrapped, mapping) => apply(wrapped, ParamsAsAny(encodedM[Any](mapping)), ov)
    }
  }

  private def charset[R](mediaType: MediaType, bodyType: RawBodyType[R]): Option[Charset] = bodyType match {
    // TODO: add to MediaType - setting optional charset if text
    case StringBody(charset) if mediaType.mainType.equalsIgnoreCase("text") => Some(charset)
    case _                                                                  => None
  }
}

case class OutputValues[B](
    body: Option[HasHeaders => B],
    baseHeaders: Vector[Header],
    headerTransformations: Vector[Vector[Header] => Vector[Header]],
    statusCode: Option[StatusCode]
) {
  def withBody(b: HasHeaders => B): OutputValues[B] = {
    if (body.isDefined) {
      throw new IllegalArgumentException("Body is already defined")
    }

    copy(body = Some(b))
  }

  def withHeaderTransformation(t: Vector[Header] => Vector[Header]): OutputValues[B] =
    copy(headerTransformations = headerTransformations :+ t)
  def withDefaultContentType(format: CodecFormat, charset: Option[Charset]): OutputValues[B] = {
    withHeaderTransformation { hs =>
      if (hs.exists(_.is(HeaderNames.ContentType))) hs
      else hs :+ Header(HeaderNames.ContentType, charset.fold(format.mediaType)(format.mediaType.charset(_)).toString())
    }
  }

  def withHeader(n: String, v: String): OutputValues[B] = copy(baseHeaders = baseHeaders :+ Header(n, v))

  def withStatusCode(sc: StatusCode): OutputValues[B] = copy(statusCode = Some(sc))

  def headers: Seq[Header] = {
    headerTransformations.foldLeft(baseHeaders) { case (hs, t) => t(hs) }
  }
}
object OutputValues {
  def empty[B]: OutputValues[B] = OutputValues[B](None, Vector.empty, Vector.empty, None)
}
