package sttp.tapir.server.internal

import java.nio.charset.Charset

import sttp.model.{HeaderNames, StatusCode}
import sttp.tapir.internal.{Params, ParamsAsAny, SplitParams}
import sttp.tapir.{CodecFormat, EndpointIO, EndpointOutput, Mapping, RawBodyType, StreamingEndpointIO}

import scala.util.Try

class EncodeOutputs[B](encodeOutputBody: EncodeOutputBody[B]) {
  def apply(output: EndpointOutput[_], value: Params, ov: OutputValues[B]): OutputValues[B] = {
    output match {
      case s: EndpointOutput.Single[_]                => applySingle(s, value, ov)
      case s: EndpointIO.Single[_]                    => applySingle(s, value, ov)
      case EndpointOutput.Pair(left, right, _, split) => applyPair(left, right, split, value, ov)
      case EndpointIO.Pair(left, right, _, split)     => applyPair(left, right, split, value, ov)
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
      case EndpointIO.FixedHeader(header, _, _)     => ov.withHeader(header.name -> header.value)
      case EndpointIO.Body(rawValueType, codec, _)  => ov.withBody(encodeOutputBody.rawValueToBody(encoded, codec.format, rawValueType))
      case EndpointIO.StreamBodyWrapper(StreamingEndpointIO.Body(codec, _, charset)) =>
        ov.withBody(encodeOutputBody.streamValueToBody(encoded, codec.format, charset))
      case EndpointIO.Header(name, _, _) =>
        encoded[List[String]].foldLeft(ov) { case (ovv, headerValue) => ovv.withHeader((name, headerValue)) }
      case EndpointIO.Headers(_, _)           => encoded[List[sttp.model.Header]].foldLeft(ov)((ov2, h) => ov2.withHeader((h.name, h.value)))
      case EndpointIO.MappedPair(wrapped, _)  => apply(wrapped, ParamsAsAny(encoded), ov)
      case EndpointOutput.StatusCode(_, _, _) => ov.withStatusCode(encoded[StatusCode])
      case EndpointOutput.OneOf(mappings, _) =>
        val enc = encoded[Any]
        val mapping = mappings
          .find(mapping => mapping.appliesTo(enc))
          .getOrElse(throw new IllegalArgumentException(s"No status code mapping for value: $enc, in output: $output"))
        apply(mapping.output, ParamsAsAny(enc), mapping.statusCode.map(ov.withStatusCode).getOrElse(ov))
      case EndpointOutput.MappedPair(wrapped, _) => apply(wrapped, ParamsAsAny(encoded), ov)
    }
  }
}

case class OutputValues[B](body: Option[B], headers: Vector[(String, String)], statusCode: Option[StatusCode]) {
  def withBody(b: B): OutputValues[B] = {
    if (body.isDefined) {
      throw new IllegalArgumentException("Body is already defined")
    }

    copy(body = Some(b))
  }

  def withHeader(h: (String, String)): OutputValues[B] = copy(headers = headers :+ h)

  def withStatusCode(sc: StatusCode): OutputValues[B] = copy(statusCode = Some(sc))

  def contentLength: Option[Long] =
    headers
      .collectFirst {
        case (k, v) if HeaderNames.ContentLength.equalsIgnoreCase(k) => v
      }
      .flatMap(v => Try(v.toLong).toOption)
}
object OutputValues {
  def empty[B]: OutputValues[B] = OutputValues[B](None, Vector.empty, None)
}

trait EncodeOutputBody[B] {
  def rawValueToBody(v: Any, format: CodecFormat, bodyType: RawBodyType[_]): B
  def streamValueToBody(v: Any, format: CodecFormat, charset: Option[Charset]): B
}
