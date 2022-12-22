package sttp.tapir.server.interpreter

import sttp.model._
import sttp.tapir.EndpointIO.OneOfBodyVariant
import sttp.tapir.EndpointOutput.OneOfVariant
import sttp.tapir.internal.{Params, ParamsAsAny, SplitParams, _}
import sttp.tapir.{Codec, CodecFormat, EndpointIO, EndpointOutput, Mapping, StreamBodyIO, WebSocketBodyOutput}

import java.nio.charset.Charset
import scala.collection.immutable.Seq

class EncodeOutputs[B, S](rawToResponseBody: ToResponseBody[B, S], acceptsContentTypes: Seq[ContentTypeRange]) {
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
        val maybeCharset = if (codec.format.mediaType.isText) charset(rawBodyType) else None
        ov.withBody(headers => rawToResponseBody.fromRawValue(encodedC(codec), headers, codec.format, rawBodyType))
          .withDefaultContentType(codec.format, maybeCharset)
      case EndpointIO.OneOfBody(variants, mapping) => applySingle(chooseOneOfVariant(variants), ParamsAsAny(encodedM[Any](mapping)), ov)
      case EndpointIO.StreamBodyWrapper(StreamBodyIO(_, codec, _, charset, _)) =>
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
      case o @ EndpointOutput.OneOf(mappings, mapping) =>
        val enc = encodedM[Any](mapping)
        val applicableMappings = mappings.filter(_.appliesTo(enc))
        if (applicableMappings.isEmpty) {
          throw new IllegalArgumentException(
            s"None of the mappings defined in the one-of output: ${o.show}, is applicable to the value: $enc. " +
              s"Verify that the type parameters to oneOf are correct, and that the oneOfVariants are exhaustive " +
              s"(that is, that they cover all possible cases)."
          )
        }

        val chosenVariant = chooseOneOfVariant(applicableMappings)
        apply(chosenVariant.output, ParamsAsAny(enc), ov)

      case EndpointOutput.MappedPair(wrapped, mapping) => apply(wrapped, ParamsAsAny(encodedM[Any](mapping)), ov)
    }
  }

  private def chooseOneOfVariant(variants: List[OneOfBodyVariant[_]]): EndpointIO.Atom[_] = {
    val mediaTypeToBody = variants.map(v => v.mediaTypeWithCharset -> v)
    chooseBestVariant[OneOfBodyVariant[_]](mediaTypeToBody).getOrElse(variants.head).bodyAsAtom
  }

  private def chooseOneOfVariant(variants: Seq[OneOfVariant[_]]): OneOfVariant[_] = {
    // #1164: there might be multiple applicable mappings, for the same content type - e.g. when there's a default
    // mapping. We need to take the first defined into account.
    val bodyVariants: Seq[(MediaType, OneOfVariant[_])] = variants
      .flatMap { om =>
        val mediaTypeFromBody = om.output.traverseOutputs {
          case b: EndpointIO.Body[_, _]              => Vector[(MediaType, OneOfVariant[_])](b.mediaTypeWithCharset -> om)
          case b: EndpointIO.StreamBodyWrapper[_, _] => Vector[(MediaType, OneOfVariant[_])](b.mediaTypeWithCharset -> om)
        }

        // #2200: some variants might have no body, which means that they match any of the `acceptsContentTypes`;
        // in this case, creating a "fake" media type which will match the first content range
        if (mediaTypeFromBody.isEmpty) {
          val fakeMediaType = acceptsContentTypes.headOption
            .map(r => MediaType(r.mainType, r.subType))
            .getOrElse(MediaType.ApplicationOctetStream)
          Vector(fakeMediaType -> om)
        } else mediaTypeFromBody
      }

    chooseBestVariant(bodyVariants).getOrElse(variants.head)
  }

  private def chooseBestVariant[T](variants: Seq[(MediaType, T)]): Option[T] = {
    if (variants.nonEmpty) {
      val mediaTypes = variants.map(_._1)
      MediaType
        .bestMatch(mediaTypes, acceptsContentTypes)
        .flatMap(mt => variants.find(_._1 == mt).map(_._2))
    } else None
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
