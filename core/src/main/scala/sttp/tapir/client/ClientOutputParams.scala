package sttp.tapir.client

import sttp.model.{HeaderNames, MediaType, ResponseMetadata}
import sttp.tapir.internal.{CombineParams, Params, ParamsAsAny, _}
import sttp.tapir.{Codec, CodecFormat, DecodeResult, EndpointIO, EndpointOutput, Mapping, StreamBodyIO, WebSocketBodyOutput}

abstract class ClientOutputParams {
  def apply(output: EndpointOutput[_], body: Any, meta: ResponseMetadata): DecodeResult[Params] =
    output match {
      case s: EndpointOutput.Single[_] =>
        (s match {
          case EndpointIO.Body(_, codec, _)                               => decode(codec, body)
          case EndpointIO.StreamBodyWrapper(StreamBodyIO(_, codec, _, _)) => decode(codec, body)
          case EndpointOutput.WebSocketBodyWrapper(o)                     => decodeWebSocketBody(o, body)
          case EndpointIO.Header(name, codec, _)                          => codec.decode(meta.headers(name).toList)
          case EndpointIO.Headers(codec, _)                               => codec.decode(meta.headers.toList)
          case EndpointOutput.StatusCode(_, codec, _)                     => codec.decode(meta.code)
          case EndpointOutput.FixedStatusCode(_, codec, _)                => codec.decode(())
          case EndpointIO.FixedHeader(_, codec, _)                        => codec.decode(())
          case EndpointIO.Empty(codec, _)                                 => codec.decode(())
          case EndpointOutput.OneOf(mappings, codec) =>
            val mappingsForStatus = mappings collect {
              case m if m.statusCode.isEmpty || m.statusCode.contains(meta.code) => m
            }

            val contentType = meta
              .header(HeaderNames.ContentType)
              .map(MediaType.parse)

            def applyMapping(m: EndpointOutput.OneOfMapping[_]) = apply(m.output, body, meta).flatMap(p => decode(codec, p.asAny))

            contentType match {
              case None =>
                val firstNonBodyMapping = mappingsForStatus.find(_.output.traverseOutputs {
                  case _ @(EndpointIO.Body(_, _, _) | EndpointIO.StreamBodyWrapper(_)) => Vector(())
                }.isEmpty)

                firstNonBodyMapping
                  .orElse(mappingsForStatus.headOption)
                  .map(applyMapping)
                  .getOrElse(
                    DecodeResult.Error(
                      meta.statusText,
                      new IllegalArgumentException(s"Cannot find mapping for status code ${meta.code} in outputs $output")
                    )
                  )

              case Some(Right(content)) =>
                val bodyMappingForStatus = mappingsForStatus collectFirst {
                  case m if m.output.hasBodyMatchingContent(content) => m
                }

                bodyMappingForStatus
                  .orElse(mappingsForStatus.headOption)
                  .map(applyMapping)
                  .getOrElse(
                    DecodeResult.Error(
                      meta.statusText,
                      new IllegalArgumentException(
                        s"Cannot find mapping for status code ${meta.code} and content $content in outputs $output"
                      )
                    )
                  )

              case Some(Left(_)) => DecodeResult.Error(meta.statusText, new IllegalArgumentException("Unable to parse Content-Type header"))
            }
          case EndpointIO.MappedPair(wrapped, codec)     => apply(wrapped, body, meta).flatMap(p => decode(codec, p.asAny))
          case EndpointOutput.MappedPair(wrapped, codec) => apply(wrapped, body, meta).flatMap(p => decode(codec, p.asAny))
        }).map(ParamsAsAny.apply)

      case EndpointOutput.Void()                        => DecodeResult.Error("", new IllegalArgumentException("Cannot convert a void output to a value!"))
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

  def decodeWebSocketBody(o: WebSocketBodyOutput[_, _, _, _, _], body: Any): DecodeResult[Any]
}
