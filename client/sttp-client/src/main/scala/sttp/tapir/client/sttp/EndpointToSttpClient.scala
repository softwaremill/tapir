package sttp.tapir.client.sttp

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import sttp.capabilities.Streams
import sttp.client3._
import sttp.model.Uri.PathSegment
import sttp.model.{HeaderNames, Method, Part, ResponseMetadata, StatusCode, Uri}
import sttp.tapir.Codec.PlainCodec
import sttp.tapir._
import sttp.tapir.internal._
import sttp.ws.WebSocket

private[sttp] class EndpointToSttpClient[R](clientOptions: SttpClientOptions, wsToPipe: WebSocketToPipe[R]) {
  def toSttpRequestUnsafe[I, E, O](e: Endpoint[I, E, O, R], baseUri: Uri): I => Request[Either[E, O], R] = { params =>
    toSttpRequest(e, baseUri)(params).mapResponse(getOrThrow)
  }

  def toSttpRequest[O, E, I](e: Endpoint[I, E, O, R], baseUri: Uri): I => Request[DecodeResult[Either[E, O]], R] = { params =>
    val (uri, req1) =
      setInputParams(
        e.input,
        ParamsAsAny(params),
        baseUri,
        basicRequest.asInstanceOf[PartialAnyRequest]
      )

    val req2 = req1.copy[Identity, Any, Any](method = sttp.model.Method(e.input.method.getOrElse(Method.GET).method), uri = uri)

    val isWebSocket = bodyIsWebSocket(e.output)
    def isSuccess(meta: ResponseMetadata) = if (isWebSocket) meta.code == StatusCode.SwitchingProtocols else meta.isSuccess
    val responseAs = fromMetadata(
      responseAsFromOutputs(e.errorOutput, isWebSocket = false),
      ConditionalResponseAs(isSuccess, responseAsFromOutputs(e.output, isWebSocket))
    ).mapWithMetadata { (body, meta) =>
      val output = if (isSuccess(meta)) e.output else e.errorOutput
      val params = getOutputParams(output, body, meta)
      params.map(_.asAny).map(p => if (isSuccess(meta)) Right(p) else Left(p))
    }.map {
      case DecodeResult.Error(o, e) =>
        DecodeResult.Error(o, new IllegalArgumentException(s"Cannot decode from $o of request ${req2.method} ${req2.uri}", e))
      case other => other
    }

    req2.response(responseAs).asInstanceOf[Request[DecodeResult[Either[E, O]], R]]
  }

  private def getOutputParams(output: EndpointOutput[_], body: Any, meta: ResponseMetadata): DecodeResult[Params] = {
    output match {
      case s: EndpointOutput.Single[_] =>
        (s match {
          case EndpointIO.Body(_, codec, _)                               => codec.decode(body)
          case EndpointIO.StreamBodyWrapper(StreamBodyIO(_, codec, _, _)) => codec.decode(body)
          case EndpointOutput.WebSocketBodyWrapper(o: WebSocketBodyOutput[_, _, _, _, Any]) =>
            val streams = o.streams.asInstanceOf[wsToPipe.S]
            o.codec.decode(
              wsToPipe
                .apply(streams)(
                  body.asInstanceOf[WebSocket[wsToPipe.F]],
                  o.asInstanceOf[WebSocketBodyOutput[Any, _, _, _, wsToPipe.S]]
                )
            )
          case EndpointIO.Header(name, codec, _)           => codec.decode(meta.headers(name).toList)
          case EndpointIO.Headers(codec, _)                => codec.decode(meta.headers.toList)
          case EndpointOutput.StatusCode(_, codec, _)      => codec.decode(meta.code)
          case EndpointOutput.FixedStatusCode(_, codec, _) => codec.decode(())
          case EndpointIO.FixedHeader(_, codec, _)         => codec.decode(())
          case EndpointIO.Empty(codec, _)                  => codec.decode(())
          case EndpointOutput.OneOf(mappings, codec) =>
            mappings
              .find(mapping => mapping.statusCode.isEmpty || mapping.statusCode.contains(meta.code)) match {
              case Some(mapping) =>
                getOutputParams(mapping.output, body, meta).flatMap(p => codec.decode(p.asAny))
              case None =>
                DecodeResult.Error(
                  meta.statusText,
                  new IllegalArgumentException(s"Cannot find mapping for status code ${meta.code} in outputs $output")
                )
            }

          case EndpointIO.MappedPair(wrapped, codec)     => getOutputParams(wrapped, body, meta).flatMap(p => codec.decode(p.asAny))
          case EndpointOutput.MappedPair(wrapped, codec) => getOutputParams(wrapped, body, meta).flatMap(p => codec.decode(p.asAny))

        }).map(ParamsAsAny)

      case EndpointOutput.Void()                        => DecodeResult.Error("", new IllegalArgumentException("Cannot convert a void output to a value!"))
      case EndpointOutput.Pair(left, right, combine, _) => handleOutputPair(left, right, combine, body, meta)
      case EndpointIO.Pair(left, right, combine, _)     => handleOutputPair(left, right, combine, body, meta)
    }
  }

  private def handleOutputPair(
      left: EndpointOutput[_],
      right: EndpointOutput[_],
      combine: CombineParams,
      body: Any,
      meta: ResponseMetadata
  ): DecodeResult[Params] = {
    val l = getOutputParams(left, body, meta)
    val r = getOutputParams(right, body, meta)
    l.flatMap(leftParams => r.map(rightParams => combine(leftParams, rightParams)))
  }

  private type PartialAnyRequest = PartialRequest[Any, Any]

  @scala.annotation.tailrec
  private def setInputParams[I](
      input: EndpointInput[I],
      params: Params,
      uri: Uri,
      req: PartialAnyRequest
  ): (Uri, PartialAnyRequest) = {
    def value: I = params.asAny.asInstanceOf[I]
    input match {
      case EndpointInput.FixedMethod(_, _, _) => (uri, req)
      case EndpointInput.FixedPath(p, _, _)   => (uri.copy(pathSegments = uri.pathSegments :+ PathSegment(p)), req)
      case EndpointInput.PathCapture(_, codec, _) =>
        val v = codec.asInstanceOf[PlainCodec[Any]].encode(value: Any)
        (uri.copy(pathSegments = uri.pathSegments :+ PathSegment(v)), req)
      case EndpointInput.PathsCapture(codec, _) =>
        val ps = codec.encode(value)
        (uri.copy(pathSegments = uri.pathSegments ++ ps.map(PathSegment(_))), req)
      case EndpointInput.Query(name, codec, _) =>
        val uri2 = codec.encode(value).foldLeft(uri) { case (u, v) => u.addParam(name, v) }
        (uri2, req)
      case EndpointInput.Cookie(name, codec, _) =>
        val req2 = codec.encode(value).foldLeft(req) { case (r, v) => r.cookie(name, v) }
        (uri, req2)
      case EndpointInput.QueryParams(codec, _) =>
        val mqp = codec.encode(value)
        val uri2 = uri.addParams(mqp.toSeq: _*)
        (uri2, req)
      case EndpointIO.Empty(_, _) => (uri, req)
      case EndpointIO.Body(bodyType, codec, _) =>
        val req2 = setBody(value, bodyType, codec, req)
        (uri, req2)
      case EndpointIO.StreamBodyWrapper(StreamBodyIO(streams, _, _, _)) =>
        val req2 = req.streamBody(streams)(value.asInstanceOf[streams.BinaryStream])
        (uri, req2)
      case EndpointIO.Header(name, codec, _) =>
        val req2 = codec
          .encode(value)
          .foldLeft(req) { case (r, v) => r.header(name, v) }
        (uri, req2)
      case EndpointIO.Headers(codec, _) =>
        val headers = codec.encode(value)
        val req2 = headers.foldLeft(req) { case (r, h) =>
          val replaceExisting = HeaderNames.ContentType.equalsIgnoreCase(h.name) || HeaderNames.ContentLength.equalsIgnoreCase(h.name)
          r.header(h, replaceExisting)
        }
        (uri, req2)
      case EndpointIO.FixedHeader(h, _, _) =>
        val req2 = req.header(h)
        (uri, req2)
      case EndpointInput.ExtractFromRequest(_, _) =>
        // ignoring
        (uri, req)
      case a: EndpointInput.Auth[_]                  => setInputParams(a.input, params, uri, req)
      case EndpointInput.Pair(left, right, _, split) => handleInputPair(left, right, params, split, uri, req)
      case EndpointIO.Pair(left, right, _, split)    => handleInputPair(left, right, params, split, uri, req)
      case EndpointInput.MappedPair(wrapped, codec)  => handleMapped(wrapped, codec.asInstanceOf[Mapping[Any, Any]], params, uri, req)
      case EndpointIO.MappedPair(wrapped, codec)     => handleMapped(wrapped, codec.asInstanceOf[Mapping[Any, Any]], params, uri, req)
    }
  }

  def handleInputPair(
      left: EndpointInput[_],
      right: EndpointInput[_],
      params: Params,
      split: SplitParams,
      uri: Uri,
      req: PartialAnyRequest
  ): (Uri, PartialAnyRequest) = {
    val (leftParams, rightParams) = split(params)
    val (uri2, req2) = setInputParams(left.asInstanceOf[EndpointInput[Any]], leftParams, uri, req)
    setInputParams(right.asInstanceOf[EndpointInput[Any]], rightParams, uri2, req2)
  }

  private def handleMapped[II, T](
      tuple: EndpointInput[II],
      codec: Mapping[T, II],
      params: Params,
      uri: Uri,
      req: PartialAnyRequest
  ): (Uri, PartialAnyRequest) = {
    setInputParams(tuple.asInstanceOf[EndpointInput[Any]], ParamsAsAny(codec.encode(params.asAny.asInstanceOf[II])), uri, req)
  }

  private def setBody[L, H, CF <: CodecFormat](
      v: H,
      bodyType: RawBodyType[L],
      codec: Codec[L, H, CF],
      req: PartialAnyRequest
  ): PartialAnyRequest = {
    val encoded = codec.encode(v)
    val req2 = bodyType match {
      case RawBodyType.StringBody(charset) => req.body(encoded, charset.name())
      case RawBodyType.ByteArrayBody       => req.body(encoded)
      case RawBodyType.ByteBufferBody      => req.body(encoded)
      case RawBodyType.InputStreamBody     => req.body(encoded)
      case RawBodyType.FileBody            => req.body(encoded)
      case m: RawBodyType.MultipartBody =>
        val parts: Seq[Part[RequestBody[Any]]] = (encoded: Seq[RawPart]).flatMap { p =>
          m.partType(p.name).map { partType =>
            // copying the name & body
            val sttpPart1 = partToSttpPart(p.asInstanceOf[Part[Any]], partType.asInstanceOf[RawBodyType[Any]])
            // copying the headers; overwriting the content type if it is specified
            val sttpPart2 = p.headers.foldLeft(sttpPart1) { (part, header) =>
              part.header(header, replaceExisting = header.is(HeaderNames.ContentType))
            }
            // copying the other disposition params (e.g. filename)
            p.otherDispositionParams.foldLeft(sttpPart2) { case (part, (k, v)) => part.dispositionParam(k, v) }
          }
        }

        req.multipartBody(parts.toList)
    }

    req2.contentType(codec.format.mediaType)
  }

  private def partToSttpPart[T](p: Part[T], bodyType: RawBodyType[T]): Part[RequestBody[Any]] =
    bodyType match {
      case RawBodyType.StringBody(charset) => multipart(p.name, p.body, charset.toString)
      case RawBodyType.ByteArrayBody       => multipart(p.name, p.body)
      case RawBodyType.ByteBufferBody      => multipart(p.name, p.body)
      case RawBodyType.InputStreamBody     => multipart(p.name, p.body)
      case RawBodyType.FileBody            => multipartFile(p.name, p.body)
      case RawBodyType.MultipartBody(_, _) => throw new IllegalArgumentException("Nested multipart bodies aren't supported")
    }

  private def responseAsFromOutputs(out: EndpointOutput[_], isWebSocket: Boolean): ResponseAs[Any, Any] = {
    ((bodyIsStream(out), isWebSocket) match {
      case (Some(streams), _) => asStreamAlwaysUnsafe(streams)
      case (_, true)          => asWebSocketAlwaysUnsafe
      case (None, false) =>
        out.bodyType
          .map {
            case RawBodyType.StringBody(charset) => asStringAlways(charset.name())
            case RawBodyType.ByteArrayBody       => asByteArrayAlways
            case RawBodyType.ByteBufferBody      => asByteArrayAlways.map(ByteBuffer.wrap)
            case RawBodyType.InputStreamBody     => asByteArrayAlways.map(new ByteArrayInputStream(_))
            case RawBodyType.FileBody            => asFileAlways(clientOptions.createFile())
            case RawBodyType.MultipartBody(_, _) => throw new IllegalArgumentException("Multipart bodies aren't supported in responses")
          }
          .getOrElse(ignore)
    }).asInstanceOf[ResponseAs[Any, Any]]
  }

  private def bodyIsStream[I](out: EndpointOutput[I]): Option[Streams[_]] = {
    out.traverseOutputs { case EndpointIO.StreamBodyWrapper(StreamBodyIO(streams, _, _, _)) =>
      Vector(streams)
    }.headOption
  }

  private def bodyIsWebSocket[I](out: EndpointOutput[I]): Boolean = {
    out.traverseOutputs { case EndpointOutput.WebSocketBodyWrapper(_) =>
      Vector(())
    }.nonEmpty
  }

  private def getOrThrow[T](dr: DecodeResult[T]): T =
    dr match {
      case DecodeResult.Value(v)    => v
      case DecodeResult.Error(_, e) => throw e
      case f                        => throw new IllegalArgumentException(s"Cannot decode: $f")
    }

}
