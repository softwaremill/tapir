package sttp.tapir.server.internal

import java.nio.charset.Charset

import sttp.model.StatusCode
import sttp.tapir.internal._
import sttp.tapir.{CodecFormat, Mapping, EndpointIO, EndpointOutput, RawBodyType, StreamingEndpointIO}

import scala.annotation.tailrec

class EncodeOutputs[B](encodeOutputBody: EncodeOutputBody[B]) {
  def apply(output: EndpointOutput[_], v: Any, initialOutputValues: OutputValues[B]): OutputValues[B] = {
    @tailrec
    def run(outputs: Vector[EndpointOutput.Single[_]], ov: OutputValues[B], vs: Seq[Any]): OutputValues[B] = {
      (outputs, vs) match {
        case (Vector(), Seq())   => ov
        case (Vector(), Seq(())) => ov
        case (outputsHead +: outputsTail, _) if outputsHead._mapping.hIsUnit =>
          val ov2 = encodeOutput(outputsHead, (), ov)
          run(outputsTail, ov2, vs)
        case (outputsHead +: outputsTail, vsHead +: vsTail) =>
          val ov2 = encodeOutput(outputsHead, vsHead, ov)
          run(outputsTail, ov2, vsTail)
        case _ =>
          throw new IllegalStateException(s"Outputs and output values don't match in output: $output, values: ${ParamsToSeq(v)}")
      }
    }

    run(output.asVectorOfSingleOutputs, initialOutputValues, ParamsToSeq(v))
  }

  private def encodeOutput(output: EndpointOutput.Single[_], value: Any, ov: OutputValues[B]): OutputValues[B] = {
    def encoded[T] = output._mapping.asInstanceOf[Mapping[T, Any]].encode(value)
    output match {
      case EndpointOutput.FixedStatusCode(sc, _, _) => ov.withStatusCode(sc)
      case EndpointIO.FixedHeader(header, _, _)     => ov.withHeader(header.name -> header.value)
      case EndpointIO.Body(rawValueType, codec, _)  => ov.withBody(encodeOutputBody.rawValueToBody(encoded, codec.format, rawValueType))
      case EndpointIO.StreamBodyWrapper(StreamingEndpointIO.Body(codec, _, charset)) =>
        ov.withBody(encodeOutputBody.streamValueToBody(encoded, codec.format, charset))
      case EndpointIO.Header(name, _, _) =>
        encoded[List[String]].foldLeft(ov) { case (ovv, headerValue) => ovv.withHeader((name, headerValue)) }
      case EndpointIO.Headers(_, _)           => encoded[List[sttp.model.Header]].foldLeft(ov)((ov2, h) => ov2.withHeader((h.name, h.value)))
      case EndpointIO.MappedTuple(wrapped, _) => apply(wrapped, encoded, ov)
      case EndpointOutput.StatusCode(_, _, _) => ov.withStatusCode(encoded[StatusCode])
      case EndpointOutput.OneOf(mappings, _) =>
        val enc = encoded[Any]
        val mapping = mappings
          .find(mapping => mapping.appliesTo(enc))
          .getOrElse(throw new IllegalArgumentException(s"No status code mapping for value: $enc, in output: $output"))
        apply(mapping.output, enc, mapping.statusCode.map(ov.withStatusCode).getOrElse(ov))
      case EndpointOutput.MappedTuple(wrapped, _) => apply(wrapped, encoded, ov)
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
}
object OutputValues {
  def empty[B]: OutputValues[B] = OutputValues[B](None, Vector.empty, None)
}

trait EncodeOutputBody[B] {
  def rawValueToBody(v: Any, format: CodecFormat, bodyType: RawBodyType[_]): B
  def streamValueToBody(v: Any, format: CodecFormat, charset: Option[Charset]): B
}
