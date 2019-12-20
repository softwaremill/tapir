package sttp.tapir.server.internal

import sttp.model.StatusCode
import sttp.tapir.CodecForMany.PlainCodecForMany
import sttp.tapir.internal._
import sttp.tapir.{CodecForOptional, CodecFormat, EndpointIO, EndpointOutput, StreamingEndpointIO}

import scala.annotation.tailrec

class EncodeOutputs[B](encodeOutputBody: EncodeOutputBody[B]) {
  def apply(output: EndpointOutput[_], v: Any, initialOutputValues: OutputValues[B]): OutputValues[B] = {
    @tailrec
    def run(outputs: Vector[EndpointOutput.Single[_]], ov: OutputValues[B], vs: Seq[Any]): OutputValues[B] = {
      (outputs, vs) match {
        case (Vector(), Seq()) => ov
        case (EndpointOutput.FixedStatusCode(sc, _) +: outputsTail, _) =>
          run(outputsTail, ov.withStatusCode(sc), vs)
        case (EndpointIO.FixedHeader(name, value, _) +: outputsTail, _) =>
          run(outputsTail, ov.withHeader(name -> value), vs)
        case (outputsHead +: outputsTail, vsHead +: vsTail) =>
          val ov2 = outputsHead match {
            case EndpointIO.Body(codec, _) =>
              codec
                .asInstanceOf[CodecForOptional[Any, _, Any]]
                .encode(vsHead)
                .map(encodeOutputBody.rawValueToBody(_, codec))
                .map(ov.withBody)
                .getOrElse(ov)
            case EndpointIO.StreamBodyWrapper(StreamingEndpointIO.Body(_, format, _)) =>
              ov.withBody(encodeOutputBody.streamValueToBody(vsHead, format))
            case EndpointIO.Header(name, codec, _) =>
              codec
                .asInstanceOf[PlainCodecForMany[Any]]
                .encode(vsHead)
                .foldLeft(ov) { case (ovv, headerValue) => ovv.withHeader((name, headerValue)) }
            case EndpointIO.Headers(_) =>
              vsHead
                .asInstanceOf[Seq[(String, String)]]
                .foldLeft(ov)(_.withHeader(_))
            case EndpointIO.Mapped(wrapped, _, g) =>
              apply(wrapped, g.asInstanceOf[Any => Any](vsHead), ov)
            case EndpointOutput.StatusCode(_) =>
              ov.withStatusCode(vsHead.asInstanceOf[StatusCode])
            case EndpointOutput.FixedStatusCode(_, _) =>
              throw new IllegalStateException("Already handled") // to make the exhaustiveness checker happy
            case EndpointIO.FixedHeader(_, _, _) =>
              throw new IllegalStateException("Already handled") // to make the exhaustiveness checker happy
            case EndpointOutput.OneOf(mappings) =>
              val mapping = mappings
                .find(mapping => mapping.appliesTo(vsHead))
                .getOrElse(throw new IllegalArgumentException(s"No status code mapping for value: $vsHead, in output: $output"))
              apply(mapping.output, vsHead, mapping.statusCode.map(ov.withStatusCode).getOrElse(ov))
            case EndpointOutput.Mapped(wrapped, _, g) =>
              apply(wrapped, g.asInstanceOf[Any => Any](vsHead), ov)
          }
          run(outputsTail, ov2, vsTail)
        case _ =>
          throw new IllegalStateException(s"Outputs and output values don't match in output: $output, values: ${ParamsToSeq(v)}")
      }
    }

    run(output.asVectorOfSingleOutputs, initialOutputValues, ParamsToSeq(v))
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
  def rawValueToBody(v: Any, codec: CodecForOptional[_, _ <: CodecFormat, Any]): B
  def streamValueToBody(v: Any, format: CodecFormat): B
}
