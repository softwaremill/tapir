package sttp.tapir.server.internal

import java.nio.charset.Charset

import sttp.model.{HeaderNames, StatusCode}
import sttp.tapir.{CodecFormat, EndpointIO, EndpointOutput, Mapping, RawBodyType, StreamingEndpointIO}

import scala.annotation.tailrec
import scala.util.Try

class EncodeOutputs[B](encodeOutputBody: EncodeOutputBody[B]) {
  def apply(output: EndpointOutput[_], value: Any, ov: OutputValues[B]): OutputValues[B] = {
    output match {
      case s: EndpointOutput.Single[_]                   => applySingle(s, value, ov)
      case s: EndpointIO.Single[_]                       => applySingle(s, value, ov)
      case EndpointOutput.Multiple(outputs, _, unParams) => applyVector(outputs, unParams(value), ov)
      case EndpointIO.Multiple(outputs, _, unParams)     => applyVector(outputs, unParams(value), ov)
      case EndpointOutput.Void()                         => throw new IllegalArgumentException("Cannot encode a void output!")
    }
  }

  @tailrec
  private def applyVector(outputs: Vector[EndpointOutput[_]], vs: Seq[Any], ov: OutputValues[B]): OutputValues[B] = {
    (outputs, vs) match {
      case (Vector(), Seq())   => ov
      case (Vector(), Seq(())) => ov
      case (outputsHead +: outputsTail, vsHead +: vsTail) =>
        val ov2 = apply(outputsHead, vsHead, ov)
        applyVector(outputsTail, vsTail, ov2)
      case _ =>
        throw new IllegalStateException(s"Outputs and output values don't match in outputs: $outputs, values: $vs")
    }
  }

  private def applySingle(output: EndpointOutput.Single[_], value: Any, ov: OutputValues[B]): OutputValues[B] = {
    def encoded[T] = output._mapping.asInstanceOf[Mapping[T, Any]].encode(value)
    output match {
      case EndpointOutput.FixedStatusCode(sc, _, _) => ov.withStatusCode(sc)
      case EndpointIO.FixedHeader(header, _, _)     => ov.withHeader(header.name -> header.value)
      case EndpointIO.Body(rawValueType, codec, _)  => ov.withBody(encodeOutputBody.rawValueToBody(encoded, codec.format, rawValueType))
      case EndpointIO.StreamBodyWrapper(StreamingEndpointIO.Body(codec, _, charset)) =>
        ov.withBody(encodeOutputBody.streamValueToBody(encoded, codec.format, charset))
      case EndpointIO.Header(name, _, _) =>
        encoded[List[String]].foldLeft(ov) { case (ovv, headerValue) => ovv.withHeader((name, headerValue)) }
      case EndpointIO.Headers(_, _)              => encoded[List[sttp.model.Header]].foldLeft(ov)((ov2, h) => ov2.withHeader((h.name, h.value)))
      case EndpointIO.MappedMultiple(wrapped, _) => apply(wrapped, encoded, ov)
      case EndpointOutput.StatusCode(_, _, _)    => ov.withStatusCode(encoded[StatusCode])
      case EndpointOutput.OneOf(mappings, _) =>
        val enc = encoded[Any]
        val mapping = mappings
          .find(mapping => mapping.appliesTo(enc))
          .getOrElse(throw new IllegalArgumentException(s"No status code mapping for value: $enc, in output: $output"))
        apply(mapping.output, enc, mapping.statusCode.map(ov.withStatusCode).getOrElse(ov))
      case EndpointOutput.MappedMultiple(wrapped, _) => apply(wrapped, encoded, ov)
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
