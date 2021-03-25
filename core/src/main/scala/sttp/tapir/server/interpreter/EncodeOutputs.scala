package sttp.tapir.server.interpreter

import sttp.model._
import sttp.model.headers.Accepts
import sttp.tapir.EndpointOutput.StatusMapping
import sttp.tapir.RawBodyType.StringBody
import sttp.tapir.internal.{Params, ParamsAsAny, SplitParams}
import sttp.tapir.{CodecFormat, EndpointIO, EndpointOutput, Mapping, RawBodyType, StreamBodyIO, WebSocketBodyOutput}

import java.nio.charset.Charset
import scala.collection.immutable.Seq

class EncodeOutputs[B, S](rawToResponseBody: ToResponseBody[B, S], requestHeaders: Seq[Header]) {
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
    def encoded[T]: T = output._mapping.asInstanceOf[Mapping[T, Any]].encode(value.asAny)
    output match {
      case EndpointIO.Empty(_, _)                   => ov
      case EndpointOutput.FixedStatusCode(sc, _, _) => ov.withStatusCode(sc)
      case EndpointIO.FixedHeader(header, _, _)     => ov.withHeader(header.name, header.value)
      case EndpointIO.Body(rawBodyType, codec, _) =>
        val maybeCharset = if (codec.format.mediaType.mainType.equalsIgnoreCase("text")) charset(rawBodyType) else None
        ov.withBody(headers => rawToResponseBody.fromRawValue(encoded[Any], headers, codec.format, rawBodyType))
          .withDefaultContentType(codec.format, maybeCharset)
      case EndpointIO.StreamBodyWrapper(StreamBodyIO(_, codec, _, charset)) =>
        ov.withBody(headers => rawToResponseBody.fromStreamValue(encoded, headers, codec.format, charset))
          .withDefaultContentType(codec.format, charset)
          .withHeaderTransformation(hs =>
            if (hs.exists(_.is(HeaderNames.ContentLength))) hs else hs :+ Header(HeaderNames.TransferEncoding, "chunked")
          )
      case EndpointIO.Header(name, _, _) =>
        encoded[List[String]].foldLeft(ov) { case (ovv, headerValue) => ovv.withHeader(name, headerValue) }
      case EndpointIO.Headers(_, _)           => encoded[List[sttp.model.Header]].foldLeft(ov)((ov2, h) => ov2.withHeader(h.name, h.value))
      case EndpointIO.MappedPair(wrapped, _)  => apply(wrapped, ParamsAsAny(encoded[Any]), ov)
      case EndpointOutput.StatusCode(_, _, _) => ov.withStatusCode(encoded[StatusCode])
      case EndpointOutput.WebSocketBodyWrapper(o) =>
        ov.withBody(_ =>
          rawToResponseBody.fromWebSocketPipe(
            encoded[rawToResponseBody.streams.Pipe[Any, Any]],
            o.asInstanceOf[WebSocketBodyOutput[rawToResponseBody.streams.Pipe[Any, Any], Any, Any, Any, S]]
          )
        )
      case EndpointOutput.OneOf(mappings, _) =>
        val enc = encoded[Any]

        val bodyMappings: Map[MediaType, StatusMapping[_]] = mappings
          .filter(_.appliesTo(enc))
          .collect({
            case sm @ StatusMapping(_, EndpointIO.Body(bodyType, codec, _), _) =>
              (charset(bodyType).map(ch => codec.format.mediaType.charset(ch.name())).getOrElse(codec.format.mediaType), sm)
            case sm @ StatusMapping(_, EndpointIO.StreamBodyWrapper(StreamBodyIO(_, codec, _, charset)), _) =>
              (charset.map(ch => codec.format.mediaType.charset(ch.name())).getOrElse(codec.format.mediaType), sm)
            case sm @ StatusMapping(_, EndpointIO.Empty(codec, _), _) => (codec.format.mediaType, sm)
          })
          .toMap

        if (bodyMappings.nonEmpty) {
          val ranges = Accepts.unsafeParse(requestHeaders)
          val mediaTypes = bodyMappings.keys.toSeq.asInstanceOf[scala.collection.immutable.Seq[MediaType]]
          MediaType
            .bestMatch(mediaTypes, ranges)
            .flatMap(bodyMappings.get)
            .map(m => apply(m.output, ParamsAsAny(enc), m.statusCode.map(ov.withStatusCode).getOrElse(ov)))
            .getOrElse(ov.withStatusCode(StatusCode.NotAcceptable))
        } else {
          val mapping = mappings
            .find(_.appliesTo(enc))
            .getOrElse(throw new IllegalArgumentException(s"No status code mapping for value: $enc, in output: $output"))
          apply(mapping.output, ParamsAsAny(enc), mapping.statusCode.map(ov.withStatusCode).getOrElse(ov))
        }
      case EndpointOutput.MappedPair(wrapped, _) => apply(wrapped, ParamsAsAny(encoded[Any]), ov)
    }
  }

  private def charset[R](bodyType: RawBodyType[R]): Option[Charset] = bodyType match {
    case StringBody(charset) => Some(charset)
    case _                   => None
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
