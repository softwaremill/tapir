package sttp.tapir.client

import sttp.model.{HeaderNames, MediaType, ResponseMetadata}
import sttp.tapir.EndpointOutput.OneOfVariant
import sttp.tapir.internal.{CombineParams, Params, ParamsAsAny, _}
import sttp.tapir.{Codec, CodecFormat, DecodeResult, EndpointIO, EndpointOutput, Mapping, StreamBodyIO, WebSocketBodyOutput}

import scala.annotation.tailrec

abstract class ClientOutputParams {
  def apply(output: EndpointOutput[_], body: Any, meta: ResponseMetadata): DecodeResult[Params] =
    output match {
      case s: EndpointOutput.Single[_] =>
        (s match {
          case EndpointIO.Body(_, codec, _) => decode(codec, body)
          case EndpointIO.OneOfBody(variants, mapping) =>
            val body2 = decode(mapping, body)
            val bodyIO = meta.contentType
              .flatMap(MediaType.parse(_).toOption)
              .flatMap(ct => variants.find(b => b.codec.format.mediaType.equalsIgnoreParameters(ct)))
              .getOrElse(variants.head)
            body2.flatMap(decode(bodyIO.codec, _))
          case EndpointIO.StreamBodyWrapper(StreamBodyIO(_, codec, _, _, _)) => decode(codec, body)
          case EndpointOutput.WebSocketBodyWrapper(o)                        => decodeWebSocketBody(o, body)
          case EndpointIO.Header(name, codec, _)                             => codec.decode(meta.headers(name).toList)
          case EndpointIO.Headers(codec, _)                                  => codec.decode(meta.headers.toList)
          case EndpointOutput.StatusCode(_, codec, _)                        => codec.decode(meta.code)
          case EndpointOutput.FixedStatusCode(sc, codec, _) =>
            if (meta.code == sc) codec.decode(()) else DecodeResult.Mismatch(sc.toString(), meta.code.toString())
          case EndpointIO.FixedHeader(h, codec, _) =>
            if (meta.header(h.name) == Option(h.value)) codec.decode(())
            else DecodeResult.Mismatch(Some(h).toString, meta.headers.find(_.is(h.name)).toString)
          case EndpointIO.Empty(codec, _) => codec.decode(())
          case EndpointOutput.OneOf(mappings, codec) =>
            val contentType = meta
              .header(HeaderNames.ContentType)
              .map(MediaType.parse)

            val mappingsFilteredByContentType: List[OneOfVariant[_]] = contentType match {
              case None | Some(Left(_)) => mappings
              case Some(Right(content)) =>
                val mappingsForContentType = mappings.collect {
                  case m if m.output.hasBodyMatchingContent(content) => m
                }
                // if there are no mappings for the content type from the response, try all
                if (mappingsForContentType.isEmpty) mappings else mappingsForContentType
            }

            tryDecodeOneOf(mappingsFilteredByContentType, body, meta, None)
              .getOrElse(
                DecodeResult.Error(
                  meta.statusText,
                  new IllegalArgumentException(
                    s"Cannot find mapping for status code ${meta.code} and content type: ${meta.header(HeaderNames.ContentType).getOrElse("-")}, in outputs $mappings"
                  )
                )
              )
              .flatMap(p => decode(codec, p.asAny))
          case EndpointIO.MappedPair(wrapped, codec)     => apply(wrapped, body, meta).flatMap(p => decode(codec, p.asAny))
          case EndpointOutput.MappedPair(wrapped, codec) => apply(wrapped, body, meta).flatMap(p => decode(codec, p.asAny))
        }).map(ParamsAsAny.apply)

      case EndpointOutput.Void() => DecodeResult.Error("", new IllegalArgumentException("Cannot convert a void output to a value!"))
      case EndpointOutput.Pair(left, right, combine, _) => handleOutputPair(left, right, combine, body, meta)
      case EndpointIO.Pair(left, right, combine, _)     => handleOutputPair(left, right, combine, body, meta)
    }

  private def handleOutputPair(
      left: EndpointOutput[_],
      right: EndpointOutput[_],
      combine: CombineParams,
      body: Any,
      meta: ResponseMetadata
  ): DecodeResult[Params] = {
    val l = apply(left, body, meta)
    val r = apply(right, body, meta)
    l.flatMap(leftParams => r.map(rightParams => combine(leftParams, rightParams)))
  }

  private def decode[L, H](codec: Codec[L, H, _ <: CodecFormat], v: Any): DecodeResult[H] = codec.decode(v.asInstanceOf[L])
  private def decode[L, H](mapping: Mapping[L, H], v: Any): DecodeResult[H] = mapping.decode(v.asInstanceOf[L])

  @tailrec
  private def tryDecodeOneOf(
      mappings: List[OneOfVariant[_]],
      body: Any,
      meta: ResponseMetadata,
      firstFailure: Option[DecodeResult.Failure]
  ): Option[DecodeResult[Params]] = mappings match {
    case Nil =>
      firstFailure match {
        case None          => None
        case Some(failure) => Some(failure)
      }
    case mapping :: other =>
      apply(mapping.output, body, meta) match {
        case v: DecodeResult.Value[_]      => Some(v)
        case failure: DecodeResult.Failure => tryDecodeOneOf(other, body, meta, firstFailure.orElse(Some(failure)))
      }
  }

  def decodeWebSocketBody(o: WebSocketBodyOutput[_, _, _, _, _], body: Any): DecodeResult[Any]
}
